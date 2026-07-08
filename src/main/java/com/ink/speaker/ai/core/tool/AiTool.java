package com.ink.speaker.ai.core.tool;

/**
 * AI 工具标记接口。
 * <p>所有供 LangChain4j AiServices 自动调用的工具类实现此接口,
 * 便于 {@link ToolRegistry} 统一扫描注册。</p>
 * <p>具体执行方法仍需标注 {@code @dev.langchain4j.agent.tool.Tool},
 * 此接口仅作 Spring Bean 类型识别与生命周期管理。</p>
 *
 * <p>对标 cc-haha {@code src/tools/Tool.ts} 的统一抽象。</p>
 *
 * <p><b>工具名约定:</b>{@link #toolName()} 用于 Skill 的 toolWhitelist 引用,
 * 必须与 {@code @Tool(name = "...")} 保持一致;默认实现通过反射读取注解,
 * 子类可在构造期缓存常量以避免运行时反射开销。</p>
 */
public interface AiTool {

    /**
     * 工具名(对应 {@code @Tool(name = ...)},用于 toolWhitelist 匹配)。
     * <p>默认实现通过反射读取 {@code @Tool} 注解的 name,
     * 若读取失败回退到类名首字母小写。</p>
     */
    default String toolName() {
        return ToolNames.fromAnnotations(getClass());
    }
}

