package ink.realm.config.mcp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 集成配置。
 * <p>P2 阶段:始终注入 {@link NoOpMcpKnowledgeSource} 作为默认实现,
 * 保证无 langchain4j-mcp 依赖时也可编译启动。</p>
 *
 * <p>用户启用真实 MCP 集成时:</p>
 * <ol>
 *   <li>pom.xml 添加 {@code langchain4j-mcp} 依赖</li>
 *   <li>新建 {@code LangChain4jMcpKnowledgeSource} 实现 {@link McpKnowledgeSource}</li>
 *   <li>在本类追加 {@code @ConditionalOnProperty(prefix="ink.mcp", name="enabled", havingValue="true")}
 *       注册该实现,并保留 NoOp 在 havingValue="false" 时生效</li>
 * </ol>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(McpProperties.class)
public class McpConfig {

    private final McpProperties properties;

    @Bean
    public McpKnowledgeSource mcpKnowledgeSource() {
        if (!properties.isEnabled()) {
            log.info("[McpConfig] enabled=false, 使用 NoOp 实现");
            return new NoOpMcpKnowledgeSource();
        }
        // P2 待办:当 properties.isEnabled() == true 时,需引入 langchain4j-mcp 依赖并构造
        // LangChain4jMcpKnowledgeSource。当前阶段统一回退到 NoOp,避免编译期耦合。
        // 见仓库 README "MCP 集成路线图" 章节。
        log.warn("[McpConfig] enabled=true 但 LangChain4j MCP 适配未实现,回退到 NoOp");
        return new NoOpMcpKnowledgeSource();
    }
}
