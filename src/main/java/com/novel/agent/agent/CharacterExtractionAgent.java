package com.novel.agent.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 人物抽取 Agent(结构化输出)
 * <p>
 * LangChain4j 最强大的能力之一:让 LLM 输出 Java 对象,而不是 String。
 * 框架自动在 Prompt 中要求 LLM 返回 JSON,并反序列化为指定类型。
 * </p>
 * <p>
 * 写作场景应用:
 *   - 从一段人物描写中抽取结构化人物卡(姓名/年龄/身份/性格/外貌);
 *   - 把对话片段分类为"日常/战斗/感情/转折"等场景类型;
 *   - 从剧情大纲中抽取关键事件要素。
 * </p>
 * <p>
 * 实现由 {@link com.novel.agent.config.AgentConfig#characterExtractionAgent} 手动构建。
 * </p>
 */
public interface CharacterExtractionAgent {

    /**
     * 信息抽取:从一段自然语言中提取人物档案
     * <p>
     * 返回 CharacterProfile 对象,框架自动处理 JSON -> POJO 的转换。
     * </p>
     */
    @SystemMessage("""
            你是人物档案整理助手。请从用户输入的文本中提取人物信息,并按 CharacterProfile 结构返回。
            字段缺失时填 null,不要编造。年龄必须是整数。
            """)
    CharacterProfile extract(@UserMessage String text);

    /**
     * 抽取结果 DTO(Java 14+ record)
     */
    record CharacterProfile(
            String name,        // 姓名
            Integer age,        // 年龄
            String identity,    // 身份/职业
            String personality, // 性格特点
            String appearance   // 外貌描述
    ) {
    }
}
