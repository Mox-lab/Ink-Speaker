package com.ink.speaker.ai.core.tool.impl;

import com.ink.speaker.ai.core.tool.AiTool;
import com.ink.speaker.ai.service.KnowledgeBaseService;
import com.ink.speaker.ai.domain.agent.LoreSearchHit;
import com.ink.speaker.config.cache.ToolCacheConfig;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工具:RAG 设定检索。
 * <p>从向量库中检索与关键词相关的设定片段,供 LLM 引用典故/历史/术语。</p>
 * <p>区别于 {@link WorldSettingQueryTool}:后者走结构化业务表(精确),
 * 本工具走向量库(模糊语义召回,覆盖知识库导入的整篇文档)。</p>
 *
 * <p>第 6 阶段(L1 缓存):RAG 向量检索成本远高于结构化查询,
 * 缓存收益最显著。同一 query 在 5min TTL 内复用 top-k 结果即可。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoreRetrieveTool implements AiTool {

    private final KnowledgeBaseService knowledgeBaseService;

    @Tool(name = "retrieveLore", value = {
            "从知识库(向量库)中检索与关键词相关的设定片段。当需要引用典故、历史、术语、长篇设定文档时调用此工具。"})
    @Cacheable(cacheManager = ToolCacheConfig.TOOL_CACHE_MANAGER,
            value = "toolLore",
            key = "#query ?: ''",
            unless = "#result == null || #result.startsWith('知识库中未检索到')")
    public String retrieveLore(@P("检索关键词或问题") String query) {
        log.info("[Tool] retrieveLore query={}", query);
        List<LoreSearchHit> hits = knowledgeBaseService.search(query);
        if (hits.isEmpty()) {
            return "知识库中未检索到相关内容";
        }
        StringBuilder sb = new StringBuilder("检索到 ").append(hits.size()).append(" 条相关片段:\n");
        for (int i = 0; i < hits.size(); i++) {
            LoreSearchHit m = hits.get(i);
            sb.append("--- 片段 ").append(i + 1).append(" (score=").append(m.score()).append(") ---\n");
            sb.append(m.text()).append("\n");
        }
        return sb.toString();
    }
}
