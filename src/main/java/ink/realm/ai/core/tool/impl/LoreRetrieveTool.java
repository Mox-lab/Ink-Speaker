package ink.realm.ai.core.tool.impl;

import ink.realm.ai.core.tool.AiTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 工具:RAG 设定检索。
 * <p>从向量库中检索与关键词相关的设定片段,供 LLM 引用典故/历史/术语。</p>
 * <p>区别于 {@link WorldSettingQueryTool}:后者走结构化业务表(精确),
 * 本工具走向量库(模糊语义召回,覆盖知识库导入的整篇文档)。</p>
 *
 * <p><b>重要约束:</b>本类<b>不能</b>被 Spring AOP 代理(原因同
 * {@link CharacterQueryTool}——否则 CGLIB 代理重写的方法丢失 {@code @Tool}
 * 注解)。缓存与向量库检索已下沉到 {@link LoreRetrieveToolCache}。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoreRetrieveTool implements AiTool {

    private final LoreRetrieveToolCache cache;

    @Tool(name = "retrieveLore", value = {
            "从知识库(向量库)中检索与关键词相关的设定片段。当需要引用典故、历史、术语、长篇设定文档时调用此工具。"})
    public String retrieveLore(@P("检索关键词或问题") String query) {
        return cache.retrieve(query);
    }
}
