package com.novel.agent.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
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
import java.util.List;

/**
 * 知识库服务:负责文档的加载、切片、向量化、入库、检索
 * <p>
 * 这是 RAG 的核心服务。完整 RAG 流程拆成两步:
 *   1. 入库(离线一次性):文档 -> 切片 -> 向量化 -> 存入 EmbeddingStore;
 *   2. 检索(在线每次):用户问题 -> 向量化 -> 相似度检索 -> 返回 Top-K 片段。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    @Value("${agent.rag-top-k:3}")
    private int topK;

    /**
     * 加载目录下所有文档并入库
     * <p>
     * 支持 pdf/docx/txt/md/html 等(Apache Tika 解析)。
     * </p>
     *
     * @param directoryPath 文档目录路径
     * @return 入库的片段总数
     */
    public int importDocuments(String directoryPath) {
        File dir = new File(directoryPath);
        if (!dir.exists() || !dir.isDirectory()) {
            log.warn("知识库目录不存在: {}", directoryPath);
            return 0;
        }

        File[] files = dir.listFiles((d, name) ->
                name.endsWith(".txt") || name.endsWith(".md")
                        || name.endsWith(".pdf") || name.endsWith(".docx"));

        if (files == null || files.length == 0) {
            log.warn("知识库目录为空: {}", directoryPath);
            return 0;
        }

        // 文档切片器:每段 300 字符,重叠 30 字符(避免切断语义)
        DocumentSplitter splitter = DocumentSplitters.recursive(300, 30);
        int totalSegments = 0;

        for (File file : files) {
            try {
                log.info("正在处理文档: {}", file.getName());
                Document doc = FileSystemDocumentLoader.loadDocument(file.getAbsolutePath());
                List<TextSegment> segments = splitter.split(doc);

                // 批量向量化并入库
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
     * 检索相关片段
     *
     * @param query 用户问题
     * @return Top-K 相似片段
     */
    public List<EmbeddingMatch<TextSegment>> search(String query) {
        Embedding queryEmb = embeddingModel.embed(query).content();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmb)
                .maxResults(topK)
                .minScore(0.6)
                .build();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
        return result.matches();
    }

    /**
     * 直接向知识库添加文本(用于代码演示,无需文件)
     */
    public void addText(String text, String metadata) {
        TextSegment seg = TextSegment.from(text);
        Embedding emb = embeddingModel.embed(text).content();
        embeddingStore.add(emb, seg);
        log.info("添加文本到知识库: {}", text.substring(0, Math.min(50, text.length())));
    }
}
