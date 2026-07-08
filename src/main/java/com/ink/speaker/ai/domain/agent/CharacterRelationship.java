package com.ink.speaker.ai.domain.agent;

/**
 * 人物关系 DTO。
 *
 * @param target 对方姓名
 * @param type   关系类型(师徒/兄弟/宿敌/恋人/父子 等)
 * @param note   关系简述(可选)
 */
public record CharacterRelationship(
        String target,
        String type,
        String note) {
}
