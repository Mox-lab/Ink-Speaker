package ink.realm.ai.service.impl;

import ink.realm.ai.cache.PromptNormalizer;
import ink.realm.ai.domain.agent.LoreSearchHit;
import ink.realm.ai.service.KnowledgeBaseService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识库服务实现。
 * <p>轻量实现:直接用 JDK NIO 读取 txt/md,不依赖 Apache Tika,避免大量传递依赖与漏洞。</p>
 *
 * <p>RAG 检索缓存(第 2 阶段):{@link #search(String)} 加 {@code @Cacheable},
 * 同一 query 在 TTL 内复用结果,避免重复调用上游 embedding API + 向量检索。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    /** RAG 检索结果缓存名(L1)。 */
    public static final String RAG_SEARCH_CACHE = "ragSearch";

    private final EmbeddingModel embeddingModel;                  // 嵌入模型,把文本转向量
    private final EmbeddingStore<TextSegment> embeddingStore;    // 向量库(PG + pgvector)
    private final PromptNormalizer promptNormalizer;             // query 规范化,提高缓存命中率

    @Value("${ink.rag-top-k:5}")
    private int topK;                                             // 检索返回的最大片段数

    @Override
    public int importDocuments(String directoryPath) {
        File dir = new File(directoryPath);
        if (!dir.exists() || !dir.isDirectory()) {
            log.warn("知识库目录不存在: {}", directoryPath);
            return 0;
        }

        File[] files = dir.listFiles((d, name) ->
                name.endsWith(".txt") || name.endsWith(".md"));
        if (files == null || files.length == 0) {
            log.warn("知识库目录为空或无 txt/md 文件: {}", directoryPath);
            return 0;
        }

        DocumentSplitter splitter = DocumentSplitters.recursive(300, 30);
        int totalSegments = 0;

        for (File file : files) {
            try {
                log.info("正在处理文档: {}", file.getName());
                String content = Files.readString(Path.of(file.getAbsolutePath()), StandardCharsets.UTF_8);
                Document doc = Document.from(content);
                List<TextSegment> segments = splitter.split(doc);

                for (TextSegment seg : segments) {
                    Embedding emb = embeddingModel.embed(seg.text()).content();
                    embeddingStore.add(emb, seg);
                    totalSegments++;
                }
                log.info("文档 {} 切片入库完成,片段数: {}", file.getName(), segments.size());
            } catch (Exception e) {
                log.error("处理文档失败: {}", file.getName(), e);
            }
        }

        log.info("知识库导入完成,总片段数: {}", totalSegments);
        return totalSegments;
    }

    /**
     * 检索相关片段(带 L1 缓存)。
     *
     * <p>同一 query 在 TTL 内复用结果,缓存 key 基于 {@link PromptNormalizer#stableKey(String...)}
     * 计算的稳定 hash,避免换行/空格差异导致缓存落空。</p>
     *
     * <p>注意:此方法返回的 {@link LoreSearchHit} 列表必须可序列化(用于 Caffeine 反序列化等场景),
     * 因此 {@link LoreSearchHit} 已是 Lombok @Builder 生成的 POJO,无不可序列化字段。</p>
     *
     * @param query 用户问题(可空,空时返回空列表)
     * @return Top-K 相似片段列表
     */
    @Override
    @Cacheable(value = RAG_SEARCH_CACHE, key = "@promptNormalizer.stableKey(#query)", unless = "#result == null || #result.isEmpty()")
    public List<LoreSearchHit> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String normalized = promptNormalizer.normalize(query);
        log.debug("[RAG] cache miss, executing embedding+search: query.len={}", normalized.length());
        Embedding queryEmb = embeddingModel.embed(normalized).content();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmb)
                .maxResults(topK)
                .minScore(0.6)
                .build();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
        List<LoreSearchHit> hits = new ArrayList<>(result.matches().size());
        for (EmbeddingMatch<TextSegment> m : result.matches()) {
            hits.add(LoreSearchHit.builder()
                    .score(m.score())
                    .text(m.embedded().text())
                    .build());
        }
        return hits;
    }

    @Override
    public void addText(String text, String metadata) {
        TextSegment seg = TextSegment.from(text);
        Embedding emb = embeddingModel.embed(text).content();
        embeddingStore.add(emb, seg);
        log.info("添加文本到知识库: {}", text.substring(0, Math.min(50, text.length())));
    }
}
