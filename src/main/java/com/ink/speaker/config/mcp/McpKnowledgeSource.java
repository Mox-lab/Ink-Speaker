package com.ink.speaker.config.mcp;

/**
 * MCP 外部知识源抽象。
 * <p>把"调用 MCP server 检索外部知识"封装为一个统一接口,
 * 便于在不引入 langchain4j-mcp 依赖时也能保持代码可编译。</p>
 *
 * <p>典型实现:</p>
 * <ul>
 *   <li>{@link NoOpMcpKnowledgeSource}:默认占位实现,返回"未启用"提示</li>
 *   <li>LangChain4jMcpKnowledgeSource:用户启用 MCP 依赖后实现的适配类</li>
 * </ul>
 */
public interface McpKnowledgeSource {

    /**
     * 调用外部 MCP server 检索知识。
     *
     * @param query 查询关键词 / 问题
     * @return 检索到的文本结果(已格式化为可直接拼到 prompt 的字符串)
     */
    String searchExternal(String query);

    /** 当前知识源是否真正可用(已连接 MCP server)。 */
    boolean isAvailable();
}
