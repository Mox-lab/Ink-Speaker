package ink.realm.config.mcp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * MCP (Model Context Protocol) 外部知识源配置。
 * <p>对标 cc-haha {@code src/services/externalKnowledge.ts}。</p>
 *
 * <p>当前阶段(P2):默认 {@code enabled=false},走 {@link NoOpMcpKnowledgeSource}
 * 返回提示信息,不依赖 langchain4j-mcp。</p>
 *
 * <p>启用步骤(用户后续接入时):</p>
 * <ol>
 *   <li>pom.xml 添加 {@code dev.langchain4j:langchain4j-mcp:1.17.1} 依赖;</li>
 *   <li>实现 {@link McpKnowledgeSource} 接口的 LangChain4j 适配类,
 *       内部用 {@code McpClient} + {@code HttpMcpTransport / StdioMcpTransport};</li>
 *   <li>在 {@link McpConfig} 中用 {@code @ConditionalOnProperty} 切换到真实实现;</li>
 *   <li>把 {@code ink.mcp.enabled} 改为 {@code true}。</li>
 * </ol>
 */
@Data
@ConfigurationProperties(prefix = "ink.mcp")
public class McpProperties {

    /** 是否启用 MCP 外部知识源集成。默认 false,避免无依赖时启动失败。 */
    private boolean enabled = false;

    /** MCP server 列表(支持多个,按顺序聚合)。 */
    private List<Server> servers = new ArrayList<>();

    /** 全局调用超时。 */
    private Duration timeout = Duration.ofSeconds(30);

    @Data
    public static class Server {
        /** server 唯一名(用于日志与隔离)。 */
        private String name = "default";
        /** transport 类型:http / stdio。 */
        private String transport = "http";
        /** HTTP SSE URL(transport=http 时使用)。 */
        private String url = "";
        /** stdio 命令(transport=stdio 时使用,如 ["uvx","mcp-server-fetch"])。 */
        private List<String> command = new ArrayList<>();
        /** 该 server 是否启用。 */
        private boolean enabled = true;
    }
}
