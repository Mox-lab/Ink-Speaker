package com.novel.forge.config;

import com.novel.forge.agent.ChapterAgent;
import com.novel.forge.agent.CharacterExtractionAgent;
import com.novel.forge.agent.LoreAgent;
import com.novel.forge.agent.OutlineAgent;
import com.novel.forge.agent.WritingAssistantAgent;
import com.novel.forge.tools.NovelTools;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 与 Memory 配置。
 * <p>手动用 AiServices.builder() 构建 Agent,便于精确控制 Memory/Tools/RAG 的注入。</p>
 */
@Configuration
public class AgentConfig {

    /**
     * 对话记忆提供者。
     * <p>每个 @MemoryId 对应一份独立的 MessageWindowChatMemory(保留最近 20 条)。</p>
     *
     * @return ChatMemoryProvider 按 memoryId 动态创建/获取 Memory
     */
    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        // Lambda 形式:输入 memoryId,返回该会话专属的 Memory 实例
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)        // 用传入的 memoryId 标识会话,实现历史隔离
                .maxMessages(20)     // 滑动窗口上限 20 条,超出自动丢弃最早消息
                .build();
    }

    /**
     * 写作助手 Agent:多轮对话 + 工具调用。
     *
     * @param chatModel          LLM 大脑(Spring 注入 ChatModel)
     * @param novelTools         小说工具集(查人物/查设定/扩写场景等)
     * @param chatMemoryProvider 多轮记忆提供者
     * @return WritingAssistantAgent 实例(由 LangChain4j 动态代理实现接口)
     */
    @Bean
    public WritingAssistantAgent writingAssistantAgent(
            ChatModel chatModel,
            NovelTools novelTools,
            ChatMemoryProvider chatMemoryProvider) {
        return AiServices.builder(WritingAssistantAgent.class)
                .chatModel(chatModel)                   // 绑定 LLM
                .tools(novelTools)                       // 注册工具,LLM 可主动调用
                .chatMemoryProvider(chatMemoryProvider)  // 启用多轮记忆
                .build();
    }

    /**
     * 章节生成 Agent:按大纲写章节,带记忆与工具。
     *
     * @param chatModel          LLM 大脑
     * @param novelTools         工具集
     * @param chatMemoryProvider 记忆提供者
     * @return ChapterAgent 实例
     */
    @Bean
    public ChapterAgent chapterAgent(
            ChatModel chatModel,
            NovelTools novelTools,
            ChatMemoryProvider chatMemoryProvider) {
        return AiServices.builder(ChapterAgent.class)
                .chatModel(chatModel)
                .tools(novelTools)
                .chatMemoryProvider(chatMemoryProvider)
                .build();
    }

    /**
     * 大纲生成 Agent:无需 Memory 与 Tools,纯生成。
     *
     * @param chatModel LLM 大脑
     * @return OutlineAgent 实例
     */
    @Bean
    public OutlineAgent outlineAgent(ChatModel chatModel) {
        return AiServices.builder(OutlineAgent.class)
                .chatModel(chatModel)
                .build();
    }

    /**
     * 设定问答 Agent(RAG):知识库问答。
     * <p>EmbeddingStoreContentRetriever 流程:问题转向量 -> 检索 top-K -> 拼接进 Prompt。</p>
     *
     * @param chatModel          LLM 大脑
     * @param embeddingModel     用于把问题/文档转向量
     * @param embeddingStore     向量库,存储设定片段
     * @param chatMemoryProvider 记忆提供者
     * @param topK               检索返回的片段数(来自 application.yml,默认 5)
     * @return LoreAgent 实例
     */
    @Bean
    public LoreAgent loreAgent(
            ChatModel chatModel,
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            ChatMemoryProvider chatMemoryProvider,
            @Value("${novel.rag-top-k:5}") int topK) {

        // 构建 RAG 检索器:把向量库 + 嵌入模型 + topK + 最低相似度阈值组合起来
        EmbeddingStoreContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)   // 指定向量库
                .embeddingModel(embeddingModel)   // 指定嵌入模型
                .maxResults(topK)                 // 返回最多 topK 条结果
                .minScore(0.6)                    // 相似度低于 0.6 的结果被丢弃,避免噪声
                .build();

        return AiServices.builder(LoreAgent.class)
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .contentRetriever(contentRetriever)  // 注入 RAG 检索器
                .build();
    }

    /**
     * 人物抽取 Agent(结构化输出)。
     *
     * @param chatModel LLM 大脑
     * @return CharacterExtractionAgent 实例
     */
    @Bean
    public CharacterExtractionAgent characterExtractionAgent(ChatModel chatModel) {
        return AiServices.builder(CharacterExtractionAgent.class)
                .chatModel(chatModel)
                .build();
    }
}
