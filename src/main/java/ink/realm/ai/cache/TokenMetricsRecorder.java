package ink.realm.ai.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * L0 Token 指标采集器:为后续 R1 优化建立"前后对比"基线。
 *
 * <p>采集指标(暴露到 Prometheus /actuator/prometheus):</p>
 * <ul>
 *   <li>{@code llm_cache_requests_total{result="hit|miss"}} — 缓存命中/未命中计数</li>
 *   <li>{@code llm_cache_size{cache="llmConcept|..."}      } — 各缓存当前条目数</li>
 *   <li>{@code llm_call_duration_seconds{agent="concept|setting|outline|polish"}} — 调用耗时</li>
 *   <li>{@code llm_estimated_tokens_saved_total}           } — 估算节省 token 数(命中时累加)</li>
 * </ul>
 *
 * <p>第 8 阶段新增 L1/L2 维度:</p>
 * <ul>
 *   <li>{@code llm_cache_requests_total{result="hit|miss",level="l1|l2|miss"}} — 区分 L1 命中 / L2 命中 / 全 miss</li>
 * </ul>
 *
 * <p>采集方式:</p>
 * <ul>
 *   <li>缓存命中率:通过 {@link CacheManager#getCache(String)} 周期采样 stats</li>
 *   <li>调用耗时:由 {@link LlmCacheService} 在 miss 时上报</li>
 *   <li>节省 token:粗略估算 = prompt 长度 / 4(Claude/OpenAI 平均 4 字符 = 1 token)</li>
 * </ul>
 */
@Slf4j
@Component
public class TokenMetricsRecorder {

    /** 缓存命中 tag。 */
    public static final String TAG_HIT = "hit";
    /** 缓存未命中 tag。 */
    public static final String TAG_MISS = "miss";

    /** L1 命中 level tag。 */
    public static final String LEVEL_L1 = "l1";
    /** L2 命中 level tag。 */
    public static final String LEVEL_L2 = "l2";
    /** L1+L2 都 miss level tag。 */
    public static final String LEVEL_MISS = "miss";

    /** token 估算系数:1 token ≈ 4 字符(中英文混合平均)。 */
    public static final int CHARS_PER_TOKEN = 4;

    private final MeterRegistry meterRegistry;
    private final CacheManager cacheManager;

    private final AtomicLong estimatedTokensSaved = new AtomicLong(0);
    private final Counter hitCounter;
    private final Counter missCounter;
    /** 第 8 阶段:区分 L1/L2/miss 的计数器。 */
    private final Counter l1HitCounter;
    private final Counter l2HitCounter;
    private final Counter totalMissCounter;

    public TokenMetricsRecorder(MeterRegistry meterRegistry,
                                @Lazy CacheManager cacheManager) {
        this.meterRegistry = meterRegistry;
        this.cacheManager = cacheManager;
        List<Tag> tags = List.of();
        this.hitCounter = Counter.builder("llm_cache_requests")
                .tags("result", TAG_HIT)
                .description("LLM cache hit/miss counter")
                .register(meterRegistry);
        this.missCounter = Counter.builder("llm_cache_requests")
                .tags("result", TAG_MISS)
                .description("LLM cache hit/miss counter")
                .register(meterRegistry);
        this.l1HitCounter = Counter.builder("llm_cache_hit_level")
                .tags("level", LEVEL_L1)
                .description("LLM cache hit by level (l1/l2)")
                .register(meterRegistry);
        this.l2HitCounter = Counter.builder("llm_cache_hit_level")
                .tags("level", LEVEL_L2)
                .description("LLM cache hit by level (l1/l2)")
                .register(meterRegistry);
        this.totalMissCounter = Counter.builder("llm_cache_hit_level")
                .tags("level", LEVEL_MISS)
                .description("LLM cache total miss counter")
                .register(meterRegistry);
        registerGauges();
    }

    /**
     * 上报一次命中。
     *
     * @param promptChars 命中的 prompt 字符数
     */
    public void recordHit(int promptChars) {
        hitCounter.increment();
        long saved = promptChars / CHARS_PER_TOKEN;
        estimatedTokensSaved.addAndGet(saved);
    }

    /**
     * 上报一次未命中。
     */
    public void recordMiss() {
        missCounter.increment();
    }

    /**
     * 第 8 阶段:上报 L1 命中。
     *
     * @param promptChars 命中的 prompt 字符数(用于估算节省 token)
     */
    public void recordL1Hit(int promptChars) {
        hitCounter.increment();
        l1HitCounter.increment();
        estimatedTokensSaved.addAndGet(promptChars / CHARS_PER_TOKEN);
    }

    /**
     * 第 8 阶段:上报 L2 命中。
     *
     * @param promptChars 命中的 prompt 字符数(用于估算节省 token)
     */
    public void recordL2Hit(int promptChars) {
        hitCounter.increment();
        l2HitCounter.increment();
        estimatedTokensSaved.addAndGet(promptChars / CHARS_PER_TOKEN);
    }

    /**
     * 第 8 阶段:上报 L1+L2 全 miss(回源 Agent)。
     */
    public void recordTotalMiss() {
        missCounter.increment();
        totalMissCounter.increment();
    }

    /**
     * 上报一次 LLM 调用耗时(miss 时调用)。
     *
     * @param agent  agent 名称(concept/setting/outline/polish)
     * @param nanos  调用耗时(纳秒)
     */
    public void recordCallDuration(String agent, long nanos) {
        Timer.builder("llm_call_duration")
                .tags("agent", agent)
                .description("LLM call duration in seconds")
                .register(meterRegistry)
                .record(nanos, TimeUnit.NANOSECONDS);
    }

    /**
     * 注册 gauge 指标(节省 token 总数)。
     */
    private void registerGauges() {
        meterRegistry.gauge("llm_estimated_tokens_saved",
                estimatedTokensSaved,
                AtomicLong::doubleValue);
    }

    /**
     * 周期采样各缓存的当前条目数,用于监控缓存规模是否异常增长。
     */
    public void sampleCacheSizes() {
        for (String name : Arrays.asList("llmConcept", "llmSetting", "llmOutline", "llmPolish", "ragSearch")) {
            Cache cache = cacheManager.getCache(name);
            if (cache == null) {
                continue;
            }
            Object nativeCache = cache.getNativeCache();
            if (nativeCache instanceof com.github.benmanes.caffeine.cache.Cache<?, ?> caffeine) {
                long size = caffeine.estimatedSize();
                meterRegistry.gauge("llm_cache_size",
                        List.of(Tag.of("cache", name)),
                        size,
                        Double::valueOf);
            }
        }
    }
}
