package com.novel.forge.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 人物抽取 Agent(结构化输出)。
 * <p>让 LLM 输出 Java 对象,框架自动 JSON -> POJO。实现由 AgentConfig#characterExtractionAgent 构建。</p>
 */
public interface CharacterExtractionAgent {

    /**
     * 从一段自然语言中提取人物档案。
     *
     * @param text 包含人物描写的文本
     * @return CharacterProfile 对象(框架自动反序列化 LLM 返回的 JSON)
     */
    @SystemMessage("""
            你是人物档案整理助手。请从用户输入的文本中提取人物信息,并按 CharacterProfile 结构返回。
            字段缺失时填 null,不要编造。年龄必须是整数。
            """)
    CharacterProfile extract(@UserMessage String text);

    /**
     * 抽取结果 DTO(Java 14+ record)。
     * <p>record 自动生成构造器/getter/equals/hashCode,适合做不可变 DTO。</p>
     *
     * @param name        姓名
     * @param age         年龄(整数;缺失为 null)
     * @param identity    身份/职业
     * @param personality 性格特点
     * @param appearance  外貌描述
     */
    record CharacterProfile(
            String name,
            Integer age,
            String identity,
            String personality,
            String appearance
    ) {
    }
}
