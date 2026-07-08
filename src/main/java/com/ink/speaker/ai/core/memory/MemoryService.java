package com.ink.speaker.ai.core.memory;

/**
 * 记忆系统统一接口。
 * <p>对标 cc-haha {@code src/services/SessionMemory.ts} + {@code extractMemories.ts} + {@code compact.ts}。
 * 本接口聚合三类能力,具体实现拆分为:</p>
 * <ul>
 *   <li>{@link SessionMemoryService} - 单会话短期记忆窗口</li>
 *   <li>{@link LongTermMemoryExtractor} - 自动抽取人物状态变化/伏笔写回业务表</li>
 *   <li>{@link ContextCompactor} - 接近 token 预算时的上下文压缩</li>
 *   <li>{@link TokenBudgetEstimator} - token 预算估算</li>
 *   <li>{@link RelevantMemoryRetriever} - RAG + 业务表混合召回</li>
 * </ul>
 */
public interface MemoryService {
}
