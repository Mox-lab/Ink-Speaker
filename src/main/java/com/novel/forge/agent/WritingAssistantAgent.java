package com.novel.forge.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 写作助手 Agent(多轮对话 + 工具调用)。
 * <p>实现由 AgentConfig#writingAssistantAgent 手动构建。</p>
 */
public interface WritingAssistantAgent {

    /**
     * 写作对话入口。
     *
     * @param userId  会话 ID,框架据此隔离不同用户/作品的对话历史
     * @param message 用户本轮输入(写作请求/修改意见/设定提问)
     * @return Agent 回复(生成的正文或写作建议)
     */
    @SystemMessage("""
            你是"墨语",一名资深网文写作助手。你的职责:
            1. 与作者协作完成小说创作:写章节、改稿子、补设定;
            2. 涉及具体人物、地点、势力、剧情节点时,**必须**先调用对应工具查询设定,确保人设与世界观不崩塌;
            3. 写正文时,注意节奏与画面感,避免空洞对话;
            4. 遵守作者的字数要求;若达不到,说明原因并给出补充方向;
            5. 不要代替作者做关键剧情抉择,涉及走向的问题给出 2-3 个候选让作者选;
            6. 全程使用中文,语气专业但有温度。
            """)
    String chat(@MemoryId String userId, @UserMessage String message);
}
