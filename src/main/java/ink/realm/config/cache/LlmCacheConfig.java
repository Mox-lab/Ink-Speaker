package ink.realm.config.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Weigher;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.List;

/**
 * LLM 响应缓存配置(L1)。
 *
 * <p>核心目标:<b>减少重复 token 消耗</b>。无状态 Agent(构思/设定/大纲/润色)
 * 的相同输入在 TTL 内复用上一次 LLM 调用结果,避免重复请求上游模型。</p>
 *
 * <p>设计要点:</p>
 * <ul>
 *   <li>按 <b>权重(字节数)</b> 限制缓存总量,而非条目数 —— LLM 响应长短差异极大
 *       (短文案 1KB,完整大纲 50KB),按条目数限制会导致大响应挤掉小响应</li>
 *   <li>内存压力监听器:JVM heap &gt; 85% 时主动清空缓存,避免 OOM</li>
 *   <li>不缓存空结果(由 {@code cache-null: false} 配置控制)</li>
 *   <li>TTL 默认 24h,通过 {@code ink.cache.llm-cache-ttl} 调整</li>
 * </ul>
 *
 * <p>使用方式:</p>
 * <pre>
 * &#64;Cacheable(value = "llmConcept", key = "#inspiration.hashCode() + '_' + (#genre ?: '')")
 * String expand(String inspiration, String genre);
 * </pre>
 *
 * <p>注意:Cache key 必须覆盖所有影响 LLM 输出的入参,否则会命中错误缓存。</p>
 */
@Slf4j
@Configuration
public class LlmCacheConfig {

    /** 单条 LLM 缓存条目的权重上限(字节,超出则跳过缓存,避免缓存超长输出)。 */
    public static final int MAX_ENTRY_WEIGHT = 64 * 1024;

    /** LLM 缓存总权重上限(字节;50MB,可由配置覆盖)。 */
    public static final long DEFAULT_MAX_WEIGHT = 50L * 1024 * 1024;

    /** heap 使用率阈值,超过则触发缓存清理。 */
    public static final double HEAP_PRESSURE_THRESHOLD = 0.85;

    /** heap 检查间隔(毫秒)。 */
    public static final long HEAP_CHECK_INTERVAL_MS = 10_000L;

    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

    private volatile CaffeineCacheManager cacheManagerInstance;

    /**
     * 缓存管理器:声明所有 LLM 响应缓存名。
     *
     * <p>缓存名约定:</p>
     * <ul>
     *   <li>{@code llmConcept}  — 构思阶段输出</li>
     *   <li>{@code llmSetting}  — 设定阶段输出</li>
     *   <li>{@code llmOutline}  — 大纲片段(每批)</li>
     *   <li>{@code llmPolish}   — 润色输出</li>
     * </ul>
     *
     * @param maxWeightBytes 缓存总权重上限(字节),来自配置 {@code ink.cache.max-weight-bytes}
     * @param ttl            单条 TTL,来自配置 {@code ink.cache.llm-cache-ttl}(ISO-8601)
     * @return Caffeine CacheManager
     */
    @Bean
    @Primary
    public CacheManager cacheManager(
            @Value("${ink.cache.max-weight-bytes:52428800}") long maxWeightBytes,
            @Value("${ink.cache.llm-cache-ttl:PT24H}") java.time.Duration ttl) {

        long effectiveMaxWeight = maxWeightBytes > 0 ? maxWeightBytes : DEFAULT_MAX_WEIGHT;
        java.time.Duration effectiveTtl = ttl == null ? java.time.Duration.ofHours(24) : ttl;

        log.info("[LlmCache] maxWeightBytes={}, ttl={}", effectiveMaxWeight, effectiveTtl);

        CaffeineCacheManager manager = new CaffeineCacheManager();
        Caffeine<Object, Object> caffeine = Caffeine.newBuilder();
        manager.setCaffeine(caffeine
                .expireAfterWrite(effectiveTtl)
                .maximumWeight(effectiveMaxWeight)
                .weigher((Weigher<Object, Object>) (key, value) -> {
                    if (value == null) {
                        return 0;
                    }
                    String s = value.toString();
                    int bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                    return bytes > MAX_ENTRY_WEIGHT ? 0 : bytes;
                })
                .recordStats()
                .removalListener((Object key, Object value, com.github.benmanes.caffeine.cache.RemovalCause cause) ->
                        log.debug("[LlmCache] evicted key={} cause={}", key, cause)));
        manager.setCacheNames(List.of("llmConcept", "llmSetting", "llmOutline", "llmPolish", "ragSearch"));
        this.cacheManagerInstance = manager;
        return manager;
    }

    /**
     * 启动后台守护线程监控 heap 使用率,超过 {@link #HEAP_PRESSURE_THRESHOLD} 时
     * 主动清空 LLM 缓存,避免 OOM。缓存可重建,OOM 不可恢复。
     */
    @PostConstruct
    public void startHeapPressureMonitor() {
        Thread monitor = new Thread(this::heapPressureLoop, "llm-cache-heap-monitor");
        monitor.setDaemon(true);
        monitor.start();
        log.info("[LlmCache] heap pressure monitor started, threshold={}, intervalMs={}",
                HEAP_PRESSURE_THRESHOLD, HEAP_CHECK_INTERVAL_MS);
    }

    /**
     * heap 监控循环:周期采样 heap 使用率,超阈值清空缓存并等待 60s 避免抖动。
     */
    private void heapPressureLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(HEAP_CHECK_INTERVAL_MS);
                double usedRatio = ((double) memoryMXBean.getHeapMemoryUsage().getUsed())
                        / memoryMXBean.getHeapMemoryUsage().getMax();
                if (usedRatio > HEAP_PRESSURE_THRESHOLD && cacheManagerInstance != null) {
                    log.warn("[LlmCache] heap pressure detected (used={}), clearing LLM caches",
                            String.format("%.2f%%", usedRatio * 100));
                    cacheManagerInstance.getCacheNames()
                            .forEach(name -> {
                                var cache = cacheManagerInstance.getCache(name);
                                if (cache != null) {
                                    cache.clear();
                                }
                            });
                    Thread.sleep(60_000L);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                log.warn("[LlmCache] heap monitor error: {}", t.getMessage());
            }
        }
    }

    /**
     * 字符串权重计算器:按 UTF-8 字节长度计权。
     *
     * <p>LLM 响应为 String 类型,直接按字节数计权最直观;超过
     * {@link #MAX_ENTRY_WEIGHT} 的条目返回 0(跳过缓存,通过其他手段控制)。</p>
     * <p>注:实际使用时由 {@code cacheManager()} 内联 lambda 实现,这里保留为
     * 工具方法便于其他场景复用。</p>
     */
    private Weigher<String, String> buildStringWeigher() {
        return (key, value) -> {
            if (value == null) {
                return 0;
            }
            int bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            return bytes > MAX_ENTRY_WEIGHT ? 0 : bytes;
        };
    }
}
