package com.novel.forge.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 大纲生成 Agent。
 * <p>给定题材/篇幅,生成 markdown 大纲。实现由 AgentConfig#outlineAgent 构建。</p>
 */
public interface OutlineAgent {

    /**
     * 大纲生成。
     *
     * @param theme   题材描述,例如 "东方玄幻+女主复仇+江湖权谋"
     * @param chapters 目标章节数
     * @return markdown 格式的大纲文本
     */
    @SystemMessage("""
            你是网文大纲策划师。请根据用户给定的题材与目标章节数,生成一份结构化大纲。
            要求:
            1. 输出 markdown 格式,层级清晰(卷 -> 章 -> 关键节点);
            2. 每章用一句话概括主线 + 1-2 个关键冲突;
            3. 标注每个"卷"的高潮点与转折点;
            4. 控制节奏:开局 3 章抓人,中段有起伏,结尾收束不烂尾;
            5. 全程使用中文。
            """)
    String generate(@UserMessage String theme, int chapters);
}
