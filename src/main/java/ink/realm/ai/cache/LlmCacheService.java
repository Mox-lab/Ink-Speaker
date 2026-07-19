package ink.realm.ai.cache;

import ink.realm.ai.agent.ConceptAgent;
import ink.realm.ai.agent.OutlineAgent;
import ink.realm.ai.agent.PolishAgent;
import ink.realm.ai.agent.SettingAgent;
import ink.realm.config.cache.MultiLevelCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ink.realm.config.cache.LlmCacheConfig;
import org.springframework.stereotype.Service;

/**
 * LLM 响应缓存包装 Service(L1+L2 多级)。
 *
 * <p>核心目的:在 Controller 与 Agent 之间加一层显式多级缓存,
 * 相同输入在 TTL 内复用结果,减少重复 token 消耗。</p>
 *
 * <p>第 8 阶段(L1+L2 改造):</p>
 * <ul>
 *   <li>原 {@code @Cacheable} 切面只走 L1(Caffeine),多实例命中率低</li>
 *   <li>改造为显式调用 {@link MultiLevelCache#get},L1 miss 回源 L2(Redis),L2 miss 回源 Agent</li>
 *   <li>避免两层代理冲突(LangChain4j AiServices 已是 JDK 动态代理,与 Spring Cache 切面不友好)</li>
 * </ul>
 *
 * <p>缓存策略:</p>
 * <ul>
 *   <li>所有 key 通过 {@link PromptNormalizer#stableKey(String...)} 计算稳定 hash</li>
 *   <li>L1 TTL 由 {@link LlmCacheConfig} 配置(默认 24h)</li>
 *   <li>L2 TTL 由 {@code ink.cache.l2-ttl} 配置(默认 24h)</li>
 *   <li>章节正文生成(ChapterAgent)与 RAG 检索不缓存,因为带 Memory 上下文,缓存收益低</li>
 *   <li>miss 时上报 {@link TokenMetricsRecorder} 用于 L0 基线对比</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmCacheService {

    private final ConceptAgent conceptAgent;
    private final SettingAgent settingAgent;
    private final OutlineAgent outlineAgent;
    private final PolishAgent polishAgent;
    private final PromptNormalizer promptNormalizer;
    private final TokenMetricsRecorder metricsRecorder;
    private final MultiLevelCache multiLevelCache;

    /**
     * 阶段 1/5 构思(带缓存)。
     *
     * <p>缓存 key 基于 inspiration 与 genre 的稳定 hash,空 genre 视为空串参与 hash。</p>
     *
     * @param inspiration 用户灵感
     * @param genre       类型(可空)
     * @return 题材蓝图
     */
    public String expandConcept(String inspiration, String genre) {
        String norm = promptNormalizer.normalize(inspiration);
        String g = genre == null ? "" : genre;
        String key = promptNormalizer.stableKey(norm, g);
        return multiLevelCache.get("llmConcept", key, () -> {
            log.debug("[LlmCache] concept miss, calling LLM: inspiration.len={}", norm.length());
            long start = System.nanoTime();
            try {
                return conceptAgent.expand(norm, g);
            } finally {
                metricsRecorder.recordMiss();
                metricsRecorder.recordCallDuration("concept", System.nanoTime() - start);
            }
        });
    }

    /**
     * 阶段 2/5 设定(带缓存)。
     *
     * @param blueprint 题材蓝图
     * @param tone      基调(可空)
     * @return 设定集
     */
    public String buildSetting(String blueprint, String tone) {
        String b = promptNormalizer.normalize(blueprint);
        String t = tone == null ? "" : tone;
        String key = promptNormalizer.stableKey(b, t);
        return multiLevelCache.get("llmSetting", key, () -> {
            log.debug("[LlmCache] setting miss, calling LLM: blueprint.len={}", b.length());
            long start = System.nanoTime();
            try {
                return settingAgent.build(b, t);
            } finally {
                metricsRecorder.recordMiss();
                metricsRecorder.recordCallDuration("setting", System.nanoTime() - start);
            }
        });
    }

    /**
     * 阶段 3/5 大纲 — 卷规划(带缓存)。
     *
     * <p>缓存 key 基于 blueprint + setting 的稳定 hash;同一题材在 TTL 内复用,避免重复规划。
     * 不再传入"目标章节数",各卷章数由模型依据题材体量自行决定。</p>
     *
     * @param blueprint 题材蓝图
     * @param setting   设定集
     * @return 卷规划 markdown
     */
    public String planVolumes(String blueprint, String setting) {
        String b = promptNormalizer.normalize(blueprint);
        String st = setting == null ? "" : setting;
        String key = promptNormalizer.stableKey(b, st);
        return multiLevelCache.get("llmOutlinePlan", key, () -> {
            log.debug("[LlmCache] outline-plan miss, calling LLM: blueprint.len={}", b.length());
            long start = System.nanoTime();
            try {
                return outlineAgent.planVolumes(b, st);
            } finally {
                metricsRecorder.recordMiss();
                metricsRecorder.recordCallDuration("outlinePlan", System.nanoTime() - start);
            }
        });
    }

    /**
     * 阶段 3/5 大纲 — 分卷展开为逐章细纲(带缓存)。
     *
     * <p>key 纳入 blueprint/setting/整书规划/前情/卷名/卷主线/起止章号,任何一项变化都视为新请求。</p>
     *
     * @param blueprint    题材蓝图(重试时含自检反馈)
     * @param setting      设定集
     * @param volumePlan   整书卷规划
     * @param prevVolumes  已完成各卷摘要
     * @param volumeName   本卷卷名
     * @param volumeArc    本卷主线
     * @param startChapter 本卷起始章号
     * @param endChapter   本卷结束章号
     * @return 本卷逐章细纲
     */
    public String expandVolume(String blueprint, String setting, String volumePlan, String prevVolumes,
                               String volumeName, String volumeArc, int startChapter, int endChapter) {
        String b = promptNormalizer.normalize(blueprint);
        String st = setting == null ? "" : setting;
        String vp = volumePlan == null ? "" : volumePlan;
        String pv = prevVolumes == null ? "" : prevVolumes;
        String vn = volumeName == null ? "" : volumeName;
        String va = volumeArc == null ? "" : volumeArc;
        String key = promptNormalizer.stableKey(b, st, vp, pv, vn, va,
                String.valueOf(startChapter), String.valueOf(endChapter));
        return multiLevelCache.get("llmOutlineExpand", key, () -> {
            log.debug("[LlmCache] outline-expand miss, calling LLM: vol={}, span={}-{}",
                    vn, startChapter, endChapter);
            long start = System.nanoTime();
            try {
                return outlineAgent.expandVolume(b, st, vp, pv, vn, va, startChapter, endChapter);
            } finally {
                metricsRecorder.recordMiss();
                metricsRecorder.recordCallDuration("outlineExpand", System.nanoTime() - start);
            }
        });
    }

    /**
     * 阶段 3/5 大纲 — 单卷自检(带缓存)。
     *
     * <p>key 基于 theme + segment + context;重试时 context 含反馈,自然不命中缓存。</p>
     *
     * @param theme    主题
     * @param segment  待检大纲片段
     * @param context  前情与全局上下文
     * @return 自检结论文本
     */
    public String selfCheck(String theme, String segment, String context) {
        String th = promptNormalizer.normalize(theme);
        String sg = segment == null ? "" : segment;
        String cx = context == null ? "" : context;
        String key = promptNormalizer.stableKey(th, sg, cx);
        return multiLevelCache.get("llmOutlineCheck", key, () -> {
            log.debug("[LlmCache] outline-check miss, calling LLM: segment.len={}", sg.length());
            long start = System.nanoTime();
            try {
                return outlineAgent.selfCheck(th, sg, cx);
            } finally {
                metricsRecorder.recordMiss();
                metricsRecorder.recordCallDuration("outlineCheck", System.nanoTime() - start);
            }
        });
    }

    /**
     * 阶段 5/5 润色(带缓存)。
     *
     * <p>focus 与 intensity 都计入 hash,保证不同参数不串结果。</p>
     *
     * @param draft     草稿
     * @param focus     润色重点
     * @param intensity 润色强度
     * @return 润色后正文
     */
    public String polish(String draft, String focus, String intensity) {
        String d = promptNormalizer.normalize(draft);
        String f = focus == null ? "" : focus;
        String i = intensity == null ? "medium" : intensity;
        String key = promptNormalizer.stableKey(d, f, i);
        return multiLevelCache.get("llmPolish", key, () -> {
            log.debug("[LlmCache] polish miss, calling LLM: draft.len={}", d.length());
            long start = System.nanoTime();
            try {
                return polishAgent.polish(d, f, i);
            } finally {
                metricsRecorder.recordMiss();
                metricsRecorder.recordCallDuration("polish", System.nanoTime() - start);
            }
        });
    }
}
