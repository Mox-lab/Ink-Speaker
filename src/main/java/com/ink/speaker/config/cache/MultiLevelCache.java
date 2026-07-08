package com.ink.speaker.config.cache;

import com.ink.speaker.ai.cache.TokenMetricsRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * L1(Caffeine) + L2(Redis) 多级缓存。
 *
 * <p>第 8 阶段:解决"多实例 L1 缓存不一致"问题。</p>
 *
 * <p>背景:LLM 缓存走 Caffeine L1(见 {@link LlmCacheConfig})后,
 * 单实例命中率约 60%;但部署到多实例后,每个实例各自维护一份 L1,
 * 同一 prompt 在不同实例上无法复用,整体命中率降到 ~20%。
 * 引入 L2 Redis 后,L1 miss 时回源 L2,L2 命中即写回 L1。</p>
 *
 * <p>读写流程:</p>
 * <ul>
 *   <li><b>读</b>:L1 hit → 返回 / L1 miss → L2 hit → 回填 L1 → 返回 / L2 miss → 调用源 → 回填 L1+L2</li>
 *   <li><b>写</b>(透写):同时写 L1 + L2,保证下次任意实例读都能命中</li>
 *   <li><b>失效</b>:通过 Redis Pub/Sub 广播 evict 事件(本阶段不实现,留到第 9 阶段压测后按需补)</li>
 * </ul>
 *
 * <p>使用方式(不依赖 @Cacheable 切面,显式调用):</p>
 * <pre>
 * String result = multiLevelCache.get("llmConcept", key, () -&gt; conceptAgent.expand(norm, g));
 * </pre>
 *
 * <p>注意:仅对"幂等且 TTL 内可复用"的 LLM 调用启用,带 Memory 上下文的 ChapterAgent 不走 L2。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MultiLevelCache {

    /** L1 缓存管理器(Caffeine,见 {@link LlmCacheConfig#cacheManager})。 */
    private final CacheManager cacheManager;

    /** L2 缓存(Redis,见 {@link RedisConfig#redisTemplate})。 */
    private final RedisTemplate<String, Object> redisTemplate;

    /** 指标采集器(第 8 阶段:区分 L1/L2/miss 上报 Prometheus)。 */
    private final TokenMetricsRecorder metricsRecorder;

    /** L2 key 前缀,避免与其他业务 key 冲突。 */
    @Value("${ink-speaker.cache.l2-prefix:ink:llm:}")
    private String l2Prefix;

    /** L2 默认 TTL(可由 cacheName 维度覆盖,见 {@link #getL2Ttl(String)})。 */
    @Value("${ink-speaker.cache.l2-ttl:PT24H}")
    private java.time.Duration l2Ttl;

    /**
     * 多级读取。
     *
     * @param cacheName L1 缓存名(如 "llmConcept")
     * @param key       缓存 key(已规范化,通常是 SHA-256 hex)
     * @param loader    L1+L2 都 miss 时的回源 loader
     * @param <T>       返回类型
     */
    public <T> T get(String cacheName, String key, Supplier<T> loader) {
        return get(cacheName, key, loader, null);
    }

    /**
     * 多级读取(可指定 L2 TTL 覆盖)。
     *
     * @param cacheName L1 缓存名
     * @param key       缓存 key
     * @param loader    回源 loader
     * @param ttlOverride 覆盖默认 L2 TTL(null 用全局配置)
     * @param <T>       返回类型
     * @return 缓存或新计算的值
     */
    public <T> T get(String cacheName, String key, Supplier<T> loader, Duration ttlOverride) {
        Cache l1 = cacheManager.getCache(cacheName);
        if (l1 == null) {
            log.warn("[MultiLevelCache] L1 cache not found: {}, fallback to loader", cacheName);
            return loader.get();
        }

        // 1. L1
        Cache.ValueWrapper l1Hit = l1.get(key);
        if (l1Hit != null && l1Hit.get() != null) {
            log.debug("[MultiLevelCache] L1 hit cache={} key={}", cacheName, key);
            metricsRecorder.recordL1Hit(key.length());
            @SuppressWarnings("unchecked")
            T value = (T) l1Hit.get();
            return value;
        }

        // 2. L2
        String l2Key = l2Key(cacheName, key);
        Object l2Hit = null;
        try {
            l2Hit = redisTemplate.opsForValue().get(l2Key);
        } catch (Exception e) {
            log.warn("[MultiLevelCache] L2 read failed cache={} key={}: {}", cacheName, key, e.getMessage());
        }
        if (l2Hit != null) {
            log.debug("[MultiLevelCache] L2 hit cache={} key={}", cacheName, key);
            metricsRecorder.recordL2Hit(key.length());
            l1.put(key, l2Hit);
            @SuppressWarnings("unchecked")
            T value = (T) l2Hit;
            return value;
        }

        // 3. miss → loader
        metricsRecorder.recordTotalMiss();
        T value = loader.get();
        if (value == null) {
            return null;
        }
        String str = value.toString();
        if (str.isBlank()) {
            return value;
        }

        // 4. 回填 L1 + L2
        l1.put(key, value);
        try {
            Duration ttl = ttlOverride != null ? ttlOverride : getL2Ttl(cacheName);
            redisTemplate.opsForValue().set(l2Key, value, ttl);
            log.debug("[MultiLevelCache] L2 write cache={} key={} ttl={}", cacheName, key, ttl);
        } catch (Exception e) {
            log.warn("[MultiLevelCache] L2 write failed cache={} key={}: {}", cacheName, key, e.getMessage());
        }
        return value;
    }

    /**
     * 主动失效(透传到 L1 + L2)。
     *
     * <p>用于设定更新后强制清掉缓存,避免脏数据。</p>
     *
     * @param cacheName L1 缓存名
     * @param key       缓存 key
     */
    public void evict(String cacheName, String key) {
        Cache l1 = cacheManager.getCache(cacheName);
        if (l1 != null) {
            l1.evict(key);
        }
        try {
            redisTemplate.delete(l2Key(cacheName, key));
        } catch (Exception e) {
            log.warn("[MultiLevelCache] L2 evict failed cache={} key={}: {}", cacheName, key, e.getMessage());
        }
    }

    /**
     * 整缓存清空(慎用,会清掉所有用户的所有 key)。
     *
     * <p>当前实现只清 L1;L2 通过 TTL 自然过期,避免误删其他实例正在使用的 key。</p>
     *
     * @param cacheName L1 缓存名
     */
    public void clear(String cacheName) {
        Cache l1 = cacheManager.getCache(cacheName);
        if (l1 != null) {
            l1.clear();
        }
        log.info("[MultiLevelCache] cleared L1 cache={}", cacheName);
    }

    /**
     * L2 key 组装:{prefix}{cacheName}:{key}。
     */
    private String l2Key(String cacheName, String key) {
        return l2Prefix + cacheName + ":" + key;
    }

    /**
     * 按 cacheName 取 L2 TTL(可后续扩展为 Map 配置)。
     */
    private Duration getL2Ttl(String cacheName) {
        return l2Ttl;
    }
}
