package com.novel.agent.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量存储与嵌入模型配置
 * <p>
 * 用于 RAG(Retrieval Augmented Generation,检索增强生成)。
 * </p>
 * <p>
 * 核心概念:
 *   - EmbeddingModel: 把文本转成向量(数组),用于计算语义相似度;
 *   - EmbeddingStore: 存储向量与原文,提供相似度检索能力;
 *   - InMemoryEmbeddingStore: 内存版向量库,演示用;生产可换 Redis/Chroma/Pinecone/Milvus。
 * </p>
 * <p>
 * 注意: 此处手动声明 AllMiniLmL6V2EmbeddingModel,
 * 因为 0.36.2 版本的 all-minilm-l6-v2 不带 Spring Boot Starter 自动配置。
 * </p>
 */
@Configuration
public class EmbeddingConfig {

    /**
     * 内存向量存储
     * <p>
     * 用于存储文档片段的向量与原文。重启即丢,生产环境请替换为 Redis/Chroma 等持久化方案。
     * </p>
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }

    /**
     * 嵌入模型(all-minilm-l6-v2,本地 ONNX 推理)
     * <p>
     * 首次使用时会从 HuggingFace 下载模型文件(约 45MB),之后缓存到本地。
     * 输出维度为 384。
     * </p>
     * <p>
     * 替代方案:
     *   - 用 Ollama 的 embedding 模型(在 application.yml 配置 langchain4j.ollama.embedding-model);
     *   - 用 OpenAI 的 text-embedding-3-small(需付费,但质量更高)。
     * </p>
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }
}
