package com.ink.speaker.ai.cache;

import com.ink.speaker.ai.agent.ConceptAgent;
import com.ink.speaker.ai.agent.OutlineAgent;
import com.ink.speaker.ai.agent.PolishAgent;
import com.ink.speaker.ai.agent.SettingAgent;
import com.ink.speaker.config.cache.MultiLevelCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 *   <li>L1 TTL 由 {@link com.ink.speaker.config.cache.LlmCacheConfig} 配置(默认 24h)</li>
 *   <li>L2 TTL 由 {@code ink-speaker.cache.l2-ttl} 配置(默认 24h)</li>
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
     * 阶段 3/5 大纲单批(带缓存)。
     *
     * <p>注意:章节数 N 的不同会产生不同 key,所以必须把 chapters 计入 hash。
     * 续生场景因含 lastOutline 不同,天然不命中,符合预期。</p>
     *
     * @param segHint  分批提示(含 blueprint + tailHint + 范围)
     * @param setting  设定集
     * @param chapters 本批章节数
     * @return 大纲片段
     */
    public String generateOutlineBatch(String segHint, String setting, int chapters) {
        String s = promptNormalizer.normalize(segHint);
        String st = setting == null ? "" : setting;
        String key = promptNormalizer.stableKey(s, st, String.valueOf(chapters));
        return multiLevelCache.get("llmOutline", key, () -> {
            log.debug("[LlmCache] outline miss, calling LLM: segHint.len={}, chapters={}", s.length(), chapters);
            long start = System.nanoTime();
            try {
                return outlineAgent.generate(s, st, chapters);
            } finally {
                metricsRecorder.recordMiss();
                metricsRecorder.recordCallDuration("outline", System.nanoTime() - start);
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
