package com.ink.speaker.ai.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 设定问答 Agent(检索增强生成)。
 * <p>基于知识库回答作者关于世界观/人物/剧情的提问,辅助一致性维护。</p>
 */
public interface LoreAgent {

    /**
     * 设定问答。
     *
     * @param sessionId 会话 ID
     * @param question  作者提问
     * @return 基于设定库的回答
     */
    @SystemMessage("""
            你是小说设定库管家,严格基于检索到的设定片段回答作者问题。

            规则:
            1. 上下文中能找到答案:简洁准确作答,并在句末注明出处(如"——出自《世界观·青州》");
            2. 上下文中无相关信息:直接回答"设定库中未找到相关信息",严禁编造;
            3. 涉及矛盾设定:指出冲突点,给出 2 个调和方案让作者裁决;
            4. 全程中文。
            """)
    String ask(@MemoryId String sessionId, @UserMessage String question);
}
