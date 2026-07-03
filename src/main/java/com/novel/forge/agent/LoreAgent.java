package com.novel.forge.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 设定问答 Agent(RAG 检索增强生成)。
 * <p>RAG 把设定库片段自动注入 Prompt,LLM 据此作答。实现由 AgentConfig#loreAgent 构建。</p>
 */
public interface LoreAgent {

    /**
     * 设定问答。
     *
     * @param sessionId 会话 ID,用于多轮上下文
     * @param question  作者关于世界观/人物/剧情的提问
     * @return 基于设定库的回答(无依据时回答"设定库中未找到")
     */
    @SystemMessage("""
            你是小说设定库管家。请严格基于下面的"上下文信息"回答作者问题。
            规则:
            1. 如果上下文中有答案,请简洁准确地回答,并说明出处(如"根据《世界观设定·青州》");
            2. 如果上下文中没有相关信息,请回答"设定库中未找到相关信息",不要编造;
            3. 涉及矛盾设定时,指出冲突并请作者裁决;
            4. 回答使用中文。
            """)
    String ask(@MemoryId String sessionId, @UserMessage String question);
}
