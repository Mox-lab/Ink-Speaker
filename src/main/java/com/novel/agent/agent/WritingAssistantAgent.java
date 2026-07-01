package com.novel.agent.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 写作助手 Agent(多轮对话 + 工具调用)
 * <p>
 * 实现由 {@link com.novel.agent.config.AgentConfig#writingAssistantAgent} 手动构建,
 * 通过 AiServices.builder() 注入 ChatModel / NovelTools / ChatMemoryProvider。
 * </p>
 * <p>
 * 工作原理:
 *   1. 用户调用 chat(userId, "帮我写林晚与苏砚在码头的初遇,1500 字");
 *   2. LangChain4j 把 systemMessage + 历史 Memory + 用户输入拼成 Prompt 发给 LLM;
 *   3. LLM 在写作过程中可主动调用 queryCharacter("林晚")、queryWorldSetting("云陵城") 等工具;
 *   4. LLM 综合设定信息生成正文,返回给用户。
 * </p>
 * <p>
 * 关键注解:
 *   - @SystemMessage: 系统提示词,定义 Agent 人设/规则;
 *   - @UserMessage:   用户消息模板;
 *   - @MemoryId:      会话标识,框架据此隔离不同用户/作品的对话历史。
 * </p>
 */
public interface WritingAssistantAgent {

    /**
     * 写作对话入口
     *
     * @param userId  会话 ID(用于隔离不同用户/作品的写作记忆)
     * @param message 用户本轮输入(可以是写作请求、修改意见、设定提问)
     * @return Agent 回复(通常是生成的正文或写作建议)
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
