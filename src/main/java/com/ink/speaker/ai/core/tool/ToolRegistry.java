package com.ink.speaker.ai.core.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册中心。
 * <p>Spring 自动注入所有 {@link AiTool} Bean,统一暴露给
 * LangChain4j {@code AiServices.tools(...)} 使用。</p>
 *
 * <p>价值:</p>
 * <ul>
 *   <li>新增工具只要新建一个 {@code @Component} 类,无需修改 AgentConfig</li>
 *   <li>支持工具列表展示、调用审计、白名单过滤(Skill.toolWhitelist 接入)</li>
 *   <li>对标 cc-haha {@code src/tools/} 的可插拔工具系统</li>
 * </ul>
 */
@Slf4j
@Component
public class ToolRegistry {

    /** 所有受管理的工具实例(按 toolName 索引,便于白名单匹配)。 */
    private final Map<String, AiTool> toolsByName = new LinkedHashMap<>();

    /** 顺序保留的工具列表(用于无白名单时的全集暴露)。 */
    private final List<AiTool> tools;

    public ToolRegistry(List<AiTool> tools) {
        // 索引构建 + 去重(同 name 时后者覆盖前者,记 warn)
        for (AiTool tool : tools) {
            String name = tool.toolName();
            if (this.toolsByName.put(name, tool) != null) {
                log.warn("[ToolRegistry] 工具名 '{}' 被多个 Bean 注册,后者覆盖前者:{}", name,
                        tool.getClass().getName());
            }
        }
        this.tools = List.copyOf(tools);
        log.info("[ToolRegistry] 注册工具 {} 个:{}", tools.size(),
                tools.stream().map(AiTool::toolName).toList());
    }

    /** 列出所有工具(只读视图)。 */
    public List<AiTool> list() {
        return tools;
    }

    /** 转为数组,传给 AiServices.builder().tools(Object...)。 */
    public Object[] asArray() {
        return tools.toArray();
    }

    /**
     * 按白名单筛选工具,返回新数组(无白名单 / 白名单为空时返回全集)。
     * <p>用于 Skill 激活时屏蔽掉无关工具,降低 LLM 误用率。</p>
     *
     * @param whitelist 允许的工具名集合;null 或空表示使用全部工具
     * @return 筛选后的工具数组
     */
    public Object[] asArray(List<String> whitelist) {
        if (whitelist == null || whitelist.isEmpty()) {
            return asArray();
        }
        return whitelist.stream()
                .map(this.toolsByName::get)
                .filter(java.util.Objects::nonNull)
                .toArray();
    }

    /** 按 toolName 取工具(主要供调试 / 单测使用)。 */
    public AiTool byName(String name) {
        return toolsByName.get(name);
    }
}
