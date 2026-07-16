package ink.realm.ai.core.tool.impl;

import ink.realm.ai.domain.agent.LoreSearchHit;
import ink.realm.ai.service.KnowledgeBaseService;
import ink.realm.config.cache.ToolCacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 设定 RAG 检索缓存层。
 * <p>将缓存注解与向量库检索下沉到本独立 Bean,使 {@link LoreRetrieveTool}
 * 不再被 Spring AOP 代理,从而保留其方法上的 LangChain4j {@code @Tool}
 * 注解(详见 {@link LoreRetrieveTool} 类注释)。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoreRetrieveToolCache {

    private final KnowledgeBaseService knowledgeBaseService;

    /** 从知识库检索设定片段(走 toolLore 缓存)。 */
    @Cacheable(cacheManager = ToolCacheConfig.TOOL_CACHE_MANAGER,
            value = "toolLore",
            key = "#query ?: ''",
            unless = "#result == null || #result.startsWith('知识库中未检索到')")
    public String retrieve(String query) {
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
