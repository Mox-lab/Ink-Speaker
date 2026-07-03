package com.novel.forge.service;

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
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识库服务:负责文档加载、切片、向量化、入库、检索。
 * <p>轻量实现:直接用 JDK NIO 读取 txt/md,不依赖 Apache Tika,避免大量传递依赖与漏洞。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final EmbeddingModel embeddingModel;                  // 嵌入模型,把文本转向量
    private final EmbeddingStore<TextSegment> embeddingStore;    // 向量库(PG + pgvector)

    @Value("${novel.rag-top-k:5}")
    private int topK;                                             // 检索返回的最大片段数(与 application.yml 中 novel.rag-top-k 对齐)

    /**
     * 加载目录下所有文档并入库。
     * <p>仅支持 txt/md(小说场景足够);如需 PDF/Word,可后续按需引入专用解析器。</p>
     *
     * @param directoryPath 文档目录路径
     * @return 入库的片段总数
     */
    public int importDocuments(String directoryPath) {
        File dir = new File(directoryPath);                       // 路径转 File
        if (!dir.exists() || !dir.isDirectory()) {                // 校验目录存在且是目录
            log.warn("知识库目录不存在: {}", directoryPath);
            return 0;
        }

        File[] files = dir.listFiles((d, name) ->                // 仅筛选 txt/md
                name.endsWith(".txt") || name.endsWith(".md"));
        if (files == null || files.length == 0) {                 // 空目录直接返回
            log.warn("知识库目录为空或无 txt/md 文件: {}", directoryPath);
            return 0;
        }

        DocumentSplitter splitter = DocumentSplitters.recursive(300, 30);  // 每段 300 字符,重叠 30
        int totalSegments = 0;                                    // 累计片段数

        for (File file : files) {                                 // 遍历每个文档
            try {
                log.info("正在处理文档: {}", file.getName());
                String content = Files.readString(Path.of(file.getAbsolutePath()), StandardCharsets.UTF_8);  // NIO 直读
                Document doc = Document.from(content);            // 包装为 LangChain4j Document
                List<TextSegment> segments = splitter.split(doc); // 切成片段

                for (TextSegment seg : segments) {                // 逐段向量化并入库
                    Embedding emb = embeddingModel.embed(seg.text()).content();  // .content() 取嵌入向量
                    embeddingStore.add(emb, seg);                 // 存入向量库
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
     * 检索相关片段。
     *
     * @param query 用户问题
     * @return Top-K 相似片段列表(按相似度降序)
     */
    public List<EmbeddingMatch<TextSegment>> search(String query) {
        Embedding queryEmb = embeddingModel.embed(query).content();   // 问题转向量
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmb)                          // 待检索的向量
                .maxResults(topK)                                  // 最多返回 topK 条
                .minScore(0.6)                                     // 相似度阈值
                .build();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);  // 执行检索
        return new ArrayList<>(result.matches());                  // 返回匹配列表
    }

    /**
     * 直接向知识库添加文本(无需文件,用于代码演示)。
     *
     * @param text     待入库的文本
     * @param metadata 元数据标识(此处未实际使用,保留扩展点)
     */
    public void addText(String text, String metadata) {
        TextSegment seg = TextSegment.from(text);                  // 把文本包装成 TextSegment
        Embedding emb = embeddingModel.embed(text).content();      // 向量化
        embeddingStore.add(emb, seg);                              // 入库
        log.info("添加文本到知识库: {}", text.substring(0, Math.min(50, text.length())));
    }
}
