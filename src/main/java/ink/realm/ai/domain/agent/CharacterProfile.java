package ink.realm.ai.domain.agent;

import java.util.List;

/**
 * 抽取结果 DTO。
 *
 * @param name         姓名
 * @param age          年龄
 * @param gender       性别(男/女/其他)
 * @param identity     身份/职业
 * @param personality  性格特点
 * @param appearance   外貌描述
 * @param weapon       武器/法宝/招牌技能
 * @param background   背景故事
 * @param relationships 与他人的关系列表
 */
public record CharacterProfile(
        String name,
        Integer age,
        String gender,
        String identity,
        String personality,
        String appearance,
        String weapon,
        String background,
        List<CharacterRelationship> relationships) {
}
