package com.ink.speaker.config.ai;

import com.ink.speaker.ai.agent.*;
import com.ink.speaker.ai.core.director.ReviewAgent;
import com.ink.speaker.ai.core.memory.L4jTokenCountEstimator;
import com.ink.speaker.ai.core.memory.TokenBudgetEstimator;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 与 Memory 配置。
 * <p>统一用 AiServices.builder() 构建,精确控制 Memory/Tools/RAG 注入。</p>
 *
 * <p>P0 重构后:</p>
 * <ul>
 *   <li>固定全工具的 Agent 仍在此构建(构思/设定/大纲/润色/审查等)</li>
 *   <li>需要按 Skill.toolWhitelist 动态过滤工具的 Agent(Chapter / WritingAssistant)
 *       不再在此构建,改由 {@link ChapterAgentFactory} / {@link WritingAssistantAgentFactory}
 *       按 whitelist 缓存构建</li>
 *   <li>新增工具只需新建 {@code @Component} 类,无需修改本配置</li>
 * </ul>
 *
 * <p>第 2 阶段(2):Memory 切换为 {@link TokenWindowChatMemory},按 token 数而非消息条数淘汰,
 * 配合 {@code ink-speaker.memory.token-budget} 精准控制上下文大小。
 * 配合 {@code ink-speaker.memory.compact-target} 由 {@link com.ink.speaker.ai.core.memory.ContextCompactor}
 * 触发主动压缩(后续阶段接入 MemoryAccessor 串联)。</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AgentConfig {

    private final TokenBudgetEstimator tokenBudgetEstimator;

    /**
     * 对话记忆提供者:每个 memoryId 独立的 Token Window。
     *
     * <p>窗口大小由 {@code ink-speaker.memory.token-budget} 控制(默认 6000),
     * 通过 {@link L4jTokenCountEstimator} 把项目内的中英文混合估算逻辑接入 LangChain4j。</p>
     *
     * <p>选择 TokenWindow 而非 MessageWindow 的原因:</p>
     * <ul>
     *   <li>小说创作场景下单条消息长度差异大(章节正文可达数千字),按消息条数限制会导致 token 溢出</li>
     *   <li>token 估算更精准,避免 LLM 上下文超限触发上游 504/413</li>
     *   <li>compact 触发阈值({@code compact-target=3000})与窗口大小({@code token-budget=6000})解耦,
     *       保留 50% 余量后再触发压缩,避免频繁 compact 影响连贯性</li>
     * </ul>
     */
    @Bean
    public ChatMemoryProvider chatMemoryProvider(
            @Value("${ink-speaker.memory.token-budget:6000}") int tokenBudget) {
        L4jTokenCountEstimator l4jEstimator = new L4jTokenCountEstimator(tokenBudgetEstimator);
        return memoryId -> TokenWindowChatMemory.builder()
                .id(memoryId)
                .maxTokens(tokenBudget, l4jEstimator)
                .build();
    }

    /** 构思 Agent(无 Memory/Tools)。 */
    @Bean
    public ConceptAgent conceptAgent(ChatModel chatModel) {
        return AiServices.builder(ConceptAgent.class)
                .chatModel(chatModel)
                .build();
    }

    /** 设定 Agent(无 Memory/Tools)。 */
    @Bean
    public SettingAgent settingAgent(ChatModel chatModel) {
        return AiServices.builder(SettingAgent.class)
                .chatModel(chatModel)
                .build();
    }

    /** 大纲 Agent(无 Memory/Tools)。 */
    @Bean
    public OutlineAgent outlineAgent(ChatModel chatModel) {
        return AiServices.builder(OutlineAgent.class)
                .chatModel(chatModel)
                .build();
    }

    /** 润色 Agent(无 Memory/Tools)。 */
    @Bean
    public PolishAgent polishAgent(ChatModel chatModel) {
        return AiServices.builder(PolishAgent.class)
                .chatModel(chatModel)
                .build();
    }

    /** 设定问答 Agent(Memory + RAG 检索)。 */
    @Bean
    public LoreAgent loreAgent(
            ChatModel chatModel,
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            ChatMemoryProvider chatMemoryProvider,
            @Value("${ink-speaker.rag-top-k:5}") int topK) {

        EmbeddingStoreContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(topK)
                .minScore(0.6)
                .build();

        return AiServices.builder(LoreAgent.class)
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .contentRetriever(contentRetriever)
                .build();
    }

    /** 人物抽取 Agent(结构化输出)。 */
    @Bean
    public CharacterExtractionAgent characterExtractionAgent(ChatModel chatModel) {
        return AiServices.builder(CharacterExtractionAgent.class)
                .chatModel(chatModel)
                .build();
    }

    /** 审查 Agent(P1 DirectorAgent 子 Agent,无 Memory/Tools)。 */
    @Bean
    public ReviewAgent reviewAgent(ChatModel chatModel) {
        return AiServices.builder(ReviewAgent.class)
                .chatModel(chatModel)
                .build();
    }
}

