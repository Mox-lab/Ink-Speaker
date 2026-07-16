package ink.realm.config.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * AI 工具调用结果缓存配置(L1)。
 *
 * <p>核心目的:<b>减少生成一章时 LLM 对相同工具参数的重复 DB/向量库查询</b>。
 * LLM 在多轮工具调用里常对同一人物/世界观/时间线连续调用多次相同参数,
 * 走 Caffeine L1 即可消除大部分重复 DB 压力。</p>
 *
 * <p>设计要点:</p>
 * <ul>
 *   <li>独立于 {@link LlmCacheConfig}:TTL 短(默认 5min),避免设定更新后脏数据持续过久</li>
 *   <li>按 <b>条目数</b> 限制(工具返回结构小,无需按字节权重)</li>
 *   <li>key 由各工具内部组装,必须包含 {@code novelId}(R5 隔离)</li>
 *   <li>不缓存 {@code SceneExpandTool}/{@code WordCountTool}/{@code ExternalKnowledgeTool}:
 *       前两者无外部依赖、计算极轻;后者走 MCP,行为外部不可预测</li>
 *   <li>Bean 名 {@code toolCacheManager},工具类用 {@code @Cacheable(cacheManager = "toolCacheManager", ...)}</li>
 * </ul>
 *
 * <p>缓存名约定:</p>
 * <ul>
 *   <li>{@code toolCharacter}    — 人物档案查询</li>
 *   <li>{@code toolWorldSetting} — 世界观查询</li>
 *   <li>{@code toolTimeline}     — 时间线查询</li>
 *   <li>{@code toolLore}         — RAG 检索结果</li>
 * </ul>
 */
@Slf4j
@Configuration
public class ToolCacheConfig {

    /** Bean 名:工具缓存管理器,供 @Cacheable 显式引用,避免与 LLM 缓存管理器冲突。 */
    public static final String TOOL_CACHE_MANAGER = "toolCacheManager";

    /**
     * 工具缓存管理器。
     *
     * @param maxSize      最大条目数(每个缓存独立计数),来自 {@code ink.cache.tool-max-size}
     * @param ttlSeconds   TTL 秒,来自 {@code ink.cache.tool-ttl-sec}
     * @return Caffeine CacheManager(名为 toolCacheManager,不与 llmCacheManager 冲突)
     */
    @Bean(TOOL_CACHE_MANAGER)
    public CacheManager toolCacheManager(
            @Value("${ink.cache.tool-max-size:2000}") int maxSize,
            @Value("${ink.cache.tool-ttl-sec:300}") long ttlSeconds) {
        log.info("[ToolCache] maxSize={}, ttlSec={}", maxSize, ttlSeconds);
        CaffeineCacheManager manager = new CaffeineCacheManager();
        Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
                .expireAfterWrite(java.time.Duration.ofSeconds(ttlSeconds))
                .maximumSize(maxSize)
                .recordStats()
                .removalListener((Object key, Object value, com.github.benmanes.caffeine.cache.RemovalCause cause) ->
                        log.debug("[ToolCache] evicted key={} cause={}", key, cause));
        manager.setCaffeine(caffeine);
        manager.setCacheNames(List.of(
                "toolCharacter", "toolWorldSetting", "toolTimeline", "toolLore"));
        return manager;
    }
}
