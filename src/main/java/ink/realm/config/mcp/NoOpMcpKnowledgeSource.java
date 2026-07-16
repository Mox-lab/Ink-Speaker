package ink.realm.config.mcp;

import lombok.extern.slf4j.Slf4j;

/**
 * MCP 知识源默认占位实现。
 * <p>当 {@code ink.mcp.enabled=false}(默认)时使用。
 * 所有方法返回"未启用"提示,不阻塞主流程。</p>
 */
@Slf4j
public class NoOpMcpKnowledgeSource implements McpKnowledgeSource {

    @Override
    public String searchExternal(String query) {
        return "[MCP 外部知识源未启用] 当前未配置 langchain4j-mcp 依赖或 ink.mcp.enabled=false,"
                + "如需调用外部知识源,请参见 McpProperties 的启用步骤。";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
