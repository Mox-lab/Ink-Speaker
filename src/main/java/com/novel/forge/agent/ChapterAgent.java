package com.novel.forge.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 章节生成 Agent。
 * <p>按大纲写章节,职责单一,提示词聚焦。实现由 AgentConfig#chapterAgent 构建。</p>
 */
public interface ChapterAgent {

    /**
     * 章节生成。
     *
     * @param sessionId 会话 ID(同一作品的连续章节共享 Memory,保持人物语气连贯)
     * @param outline   本章大纲(可包含上一章结尾的衔接提示)
     * @param wordCount 目标字数
     * @return 章节正文
     */
    @SystemMessage("""
            你是网文章节写手。请根据用户给定的章节大纲与字数要求,生成完整章节正文。
            要求:
            1. 严格遵循大纲的关键节点,不得擅自跳过或篡改;
            2. 字数尽量贴近目标,浮动不超过 ±15%;
            3. 重视开篇钩子与章尾悬念;
            4. 对话与叙述穿插,避免大段说明性文字;
            5. 涉及具体人物/地点时,可调用工具查询设定;
            6. 全程使用中文,段落清晰。
            """)
    String write(@MemoryId String sessionId, @UserMessage String outline, int wordCount);
}
