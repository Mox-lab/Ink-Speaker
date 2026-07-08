package com.ink.speaker.ai.core.tool.impl;

import com.ink.speaker.ai.core.tool.AiTool;
import com.ink.speaker.config.mcp.McpKnowledgeSource;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 工具:检索外部知识源(MCP)。
 * <p>P2 新增。把 MCP 外部知识源调用封装为一个 LangChain4j 工具,
 * 当 LLM 在写作时遇到本地知识库未覆盖的领域知识(如历史细节、地理常识、专业术语),
 * 可主动调用此工具从 MCP server 检索。</p>
 *
 * <p>当前阶段默认走 {@link com.ink.speaker.config.mcp.NoOpMcpKnowledgeSource},
 * 启用真实 MCP 集成后自动生效,无需修改本类。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalKnowledgeTool implements AiTool {

    private final McpKnowledgeSource mcpKnowledgeSource;

    @Tool(name = "searchExternalKnowledge", value = {
            "检索外部知识源(MCP server)。当本地设定库未覆盖到某个领域知识",
            "(如历史细节、地理常识、专业术语、典故出处)时调用此工具。",
            "传入明确的检索关键词,工具会返回 MCP server 的检索结果文本。"})
    public String searchExternalKnowledge(
            @P("检索关键词或问题,例如 唐朝官制、伦敦塔桥历史") String query) {
        log.info("[Tool] searchExternalKnowledge query={}", query);

        if (!mcpKnowledgeSource.isAvailable()) {
            return "外部知识源未启用,无法检索 '" + query + "'。可基于已有设定与常识合理推断,但需在前端标注'待核实'。";
        }

        try {
            String result = mcpKnowledgeSource.searchExternal(query);
            log.info("[Tool] searchExternalKnowledge query='{}' resultLen={}", query,
                    result == null ? 0 : result.length());
            return result == null ? "" : result;
        } catch (Exception e) {
            log.warn("[Tool] searchExternalKnowledge 失败 query='{}':{}", query, e.getMessage());
            return "外部知识源检索失败:" + e.getMessage();
        }
    }
}
