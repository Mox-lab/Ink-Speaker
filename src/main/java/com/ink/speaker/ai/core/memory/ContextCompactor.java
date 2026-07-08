package com.ink.speaker.ai.core.memory;

import com.ink.speaker.ai.agent.PolishAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 上下文压缩器。
 * <p>对标 cc-haha {@code src/services/compact.ts}。
 * 当会话上下文接近 token 预算时,调用一次 LLM 对旧消息做摘要,
 * 把窗口里的"事实"压成一段总结,腾出空间继续对话。</p>
 *
 * <p>策略:</p>
 * <ol>
 *   <li>估算当前上下文 token 数({@link TokenBudgetEstimator})</li>
 *   <li>若超过 {@code token-budget},取窗口前 70% 的消息</li>
 *   <li>调用 LLM 生成"前情提要"(复用 PolishAgent 的 ChatModel)</li>
 *   <li>用摘要替换前 70% 消息,保留最近 30% 原文</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextCompactor {

    private final PolishAgent polishAgent;
    private final TokenBudgetEstimator tokenBudgetEstimator;

    @Value("${ink-speaker.memory.token-budget:6000}")
    private int tokenBudget;

    @Value("${ink-speaker.memory.compact-target:3000}")
    private int compactTarget;

    /**
     * 判断是否需要压缩。
     */
    public boolean needsCompact(String context) {
        return tokenBudgetEstimator.exceeds(context, tokenBudget);
    }

    /**
     * 把"前情文本"压缩成简短摘要。
     * <p>当前实现直接复用 PolishAgent 的 ChatModel,
     * 真正的"对话消息级压缩"需要扩展 LangChain4j ChatMemory,
     * 此方法提供核心能力,后续接入 MemoryAccessor 后即可串联。</p>
     *
     * @param previousContext 待压缩的前情文本
     * @return 压缩后的摘要(目标 token 数 {@code compact-target / 1.5} 字符)
     */
    public String compact(String previousContext) {
        if (previousContext == null || previousContext.isBlank()) {
            return "";
        }
        if (!needsCompact(previousContext)) {
            return previousContext;
        }
        log.info("[ContextCompactor] 压缩前字符数={}, 目标字符数≈{}",
                previousContext.length(), (int) (compactTarget / 1.5));

        // 复用 PolishAgent 的底层 ChatModel 做"摘要"任务
        // focus=摘要,intensity=heavy,借助其 system prompt 的"不改变事实"铁律
        String focused = "摘要,保留人物状态变化、关键伏笔、时间锚点,删除冗余对话与描写";
        return polishAgent.polish(previousContext, focused, "heavy");
    }
}
