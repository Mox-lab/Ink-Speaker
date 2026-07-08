package com.ink.speaker.ai.core.tool;

import dev.langchain4j.agent.tool.Tool;

import java.lang.reflect.Method;

/**
 * 工具名解析辅助。
 * <p>从 {@link AiTool} 实现类的 {@code @Tool} 注解中提取 name,
 * 供 {@link ToolRegistry} 与 {@code Skill.toolWhitelist} 匹配使用。</p>
 */
final class ToolNames {

    private ToolNames() {
    }

    /**
     * 扫描类中第一个标注 {@code @Tool} 的方法,返回其 name。
     * <p>找不到时回退到类名首字母小写。</p>
     */
    static String fromAnnotations(Class<?> clazz) {
        if (clazz == null) return "unknown";
        for (Method m : clazz.getDeclaredMethods()) {
            Tool t = m.getAnnotation(Tool.class);
            if (t != null) {
                String name = t.name();
                if (name != null && !name.isBlank()) {
                    return name;
                }
            }
        }
        // 回退:类名首字母小写
        String simple = clazz.getSimpleName();
        return simple.isEmpty()
                ? "unknown"
                : Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    }}
