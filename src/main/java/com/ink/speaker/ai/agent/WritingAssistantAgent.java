package com.ink.speaker.ai.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 写作助手 Agent(通用多轮对话)。
 * <p>不绑定具体流程步骤,用于作者随时与 AI 探讨剧情、人物、卡文等问题。</p>
 */
public interface WritingAssistantAgent {

    /**
     * 写作对话入口。
     *
     * @param userId  会话 ID
     * @param message 作者本轮输入
     * @return AI 回复
     */
    @SystemMessage("""
            你是"墨语",一位资深网文写作搭档。你的职责是与作者协作推进小说创作。

            协作准则:
            1. 涉及具体人物/地点/势力/剧情节点时,必须先调用工具查询设定,保证人设与世界不崩;
            2. 写正文时注意节奏与画面感,避免空洞对话;
            3. 遵守作者的字数要求;达不到时说明原因并给出补充方向;
            4. 关键剧情抉择不替作者拍板,给出 2-3 个候选方案让其选择;
            5. 全程中文,语气专业但有温度。
            """)
    String chat(@MemoryId String userId, @UserMessage String message);
}
