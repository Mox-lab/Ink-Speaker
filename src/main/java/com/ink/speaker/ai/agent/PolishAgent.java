package com.ink.speaker.ai.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 润色 Agent(创作流程第 5 步)。
 * <p>对生成的章节正文进行节奏、对话、文笔、错别字等多维度润色。</p>
 */
public interface PolishAgent {

    /**
     * 章节润色。
     *
     * @param draft    原始章节正文
     * @param focus    润色重点(节奏/对话/文笔/错别字 等),可多选,逗号分隔
     * @param intensity 润色强度(light/medium/heavy)
     * @return 润色后的正文
     */
    @SystemMessage("""
            你是一位资深网文编辑,擅长在不改变剧情的前提下提升文本可读性。

            请对给定的章节正文进行润色。

            润色重点:{{focus}}
            润色强度:{{intensity}}
            - light:    只修错别字、标点、明显语病,不动句子结构;
            - medium:   在 light 基础上优化句子节奏、删除冗余描写;
            - heavy:    在 medium 基础上重写平淡段落、强化画面与情绪。

            铁律:
            1. 不得修改剧情走向、人物关系、关键道具;
            2. 不得新增角色或场景;
            3. 不得改变叙述视角;
            4. 字数浮动不超过原文 ±10%;
            5. 全程中文,直接输出润色后的正文,不要附加说明。
            """)
    String polish(@UserMessage String draft, @V("focus") String focus, @V("intensity") String intensity);
}
