package com.novel.forge.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 向量存储与嵌入模型配置(RAG 必需)。
 */
@Configuration
public class EmbeddingConfig {

    /**
     * 内存向量存储。
     * <p>重启即丢;生产环境请替换为 Redis/Chroma/Pinecone/Milvus 等持久化方案。</p>
     *
     * @return InMemoryEmbeddingStore 实例,存放 TextSegment 与对应向量
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(
            DataSource dataSource,
            @Value("${novel.pgvector.table}") String table,
            @Value("${novel.pgvector.dimension}") int dim,
            @Value("${novel.pgvector.use-index}") boolean useIndex,
            @Value("${novel.pgvector.index-list-size:100}") int indexListSize) {

        // datasourceBuilder:复用 Spring 的 DataSource 连接池,避免 PgVectorEmbeddingStore 内部再建一个
        // useIndex=true 时,源码会强制校验 indexListSize>0(IVFFlat 的 lists 参数)
        // 经验值:sqrt(行数);几千~几万条数据用 100 比较合适
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table(table)
                .dimension(dim)
                .useIndex(useIndex)
                .indexListSize(indexListSize)
                .build();
    }

    /**
     * 嵌入模型(all-minilm-l6-v2,本地 ONNX 推理,384 维)。
     * <p>首次使用会从 HuggingFace 下载约 45MB 模型文件并缓存到本地。</p>
     *
     * @return AllMiniLmL6V2EmbeddingModel 实例
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        // 本地 ONNX 运行时,无需调用外部 API,适合离线演示
        return new AllMiniLmL6V2EmbeddingModel();
    }
}
