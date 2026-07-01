package com.novel.agent.config;

import com.novel.agent.agent.ChapterAgent;
import com.novel.agent.agent.CharacterExtractionAgent;
import com.novel.agent.agent.LoreAgent;
import com.novel.agent.agent.OutlineAgent;
import com.novel.agent.agent.WritingAssistantAgent;
import com.novel.agent.tools.NovelTools;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 与 Memory 配置
 * <p>
 * 通过 AiServices.builder() 手动构建 Agent Bean,而非 @AiService 注解。
 * 原因:手动构建对 ChatMemoryProvider / ContentRetriever / Tools 的注入更直观可控,
 * 也避免不同版本注解属性差异带来的兼容性问题。
 * </p>
 * <p>
 * 核心概念 - ChatMemory:
 *   - MessageWindowChatMemory: 滑动窗口记忆,保留最近 N 条消息(默认 10),超出自动丢弃最早的;
 *   - TokenWindowChatMemory:   按 token 数量限制记忆(更精确,适合长对话);
 *   - 生产环境可自定义实现,把记忆持久化到 Redis/数据库。
 * </p>
 * <p>
 * 小说写作场景的 Memory 取舍:
 *   - 写章节时历史很重要(保持人物语气连贯),maxMessages 调大一些;
 *   - 但太大 token 消耗高,且超出模型上下文长度。20 条是经验值。
 * </p>
 */
@Configuration
public class AgentConfig {

    /**
     * 对话记忆提供者
     * <p>
     * 每个会话(@MemoryId)拥有独立的 ChatMemory 实例。
     * 这里用滑动窗口策略,保留最近 20 条消息。
     * </p>
     */
    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(20)
                .build();
    }

    /**
     * 写作助手 Agent:多轮对话 + 工具调用
     * <p>
     * 注入:
     *   - chatModel:        LLM 大脑
     *   - novelTools:       小说工具集(查人物/查设定/扩写场景等)
     *   - chatMemoryProvider: 多轮记忆
     * </p>
     */
    @Bean
    public WritingAssistantAgent writingAssistantAgent(
            ChatLanguageModel chatModel,
            NovelTools novelTools,
            ChatMemoryProvider chatMemoryProvider) {
        return AiServices.builder(WritingAssistantAgent.class)
                .chatLanguageModel(chatModel)
                .tools(novelTools)
                .chatMemoryProvider(chatMemoryProvider)
                .build();
    }

    /**
     * 章节生成 Agent:按大纲写章节,带记忆与工具
     */
    @Bean
    public ChapterAgent chapterAgent(
            ChatLanguageModel chatModel,
            NovelTools novelTools,
            ChatMemoryProvider chatMemoryProvider) {
        return AiServices.builder(ChapterAgent.class)
                .chatLanguageModel(chatModel)
                .tools(novelTools)
                .chatMemoryProvider(chatMemoryProvider)
                .build();
    }

    /**
     * 大纲生成 Agent:无需 Memory 与 Tools,纯生成
     */
    @Bean
    public OutlineAgent outlineAgent(ChatLanguageModel chatModel) {
        return AiServices.builder(OutlineAgent.class)
                .chatLanguageModel(chatModel)
                .build();
    }

    /**
     * 设定问答 Agent(RAG):知识库问答
     * <p>
     * EmbeddingStoreContentRetriever 工作流程:
     *   1. 接收用户问题;
     *   2. 调用 embeddingModel 把问题转向量;
     *   3. 在 embeddingStore 中检索 top-K 相似片段;
     *   4. 把片段作为"上下文"自动拼接到 Prompt 中。
     * </p>
     */
    @Bean
    public LoreAgent loreAgent(
            ChatLanguageModel chatModel,
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            ChatMemoryProvider chatMemoryProvider,
            @Value("${novel.rag-top-k:5}") int topK) {

        EmbeddingStoreContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(topK)
                .minScore(0.6)
                .build();

        return AiServices.builder(LoreAgent.class)
                .chatLanguageModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .contentRetriever(contentRetriever)
                .build();
    }

    /**
     * 人物抽取 Agent(结构化输出)
     */
    @Bean
    public CharacterExtractionAgent characterExtractionAgent(ChatLanguageModel chatModel) {
        return AiServices.builder(CharacterExtractionAgent.class)
                .chatLanguageModel(chatModel)
                .build();
    }
}
