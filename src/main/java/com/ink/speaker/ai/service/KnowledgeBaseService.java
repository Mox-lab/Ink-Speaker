package com.ink.speaker.ai.service;

import com.ink.speaker.ai.domain.agent.LoreSearchHit;

import java.util.List;

/**
 * 知识库服务接口。
 * <p>负责文档加载、切片、向量化、入库、检索。</p>
 */
public interface KnowledgeBaseService {

    /**
     * 加载目录下所有文档并入库。
     *
     * @param directoryPath 文档目录路径
     * @return 入库的片段总数
     */
    int importDocuments(String directoryPath);

    /**
     * 检索相关片段。
     *
     * @param query 用户问题
     * @return Top-K 相似片段列表(按相似度降序)
     */
    List<LoreSearchHit> search(String query);

    /**
     * 直接向知识库添加文本。
     *
     * @param text     待入库的文本
     * @param metadata 元数据标识
     */
    void addText(String text, String metadata);
}
