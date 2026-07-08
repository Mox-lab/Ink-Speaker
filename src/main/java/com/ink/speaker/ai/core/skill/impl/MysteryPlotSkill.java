package com.ink.speaker.ai.core.skill.impl;

import com.ink.speaker.ai.core.skill.Skill;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 推理布局技能。
 * <p>当主题包含"案件、线索、不在场证明、密室、侦探、凶手"等关键词时激活。</p>
 * <p>核心价值:强调"线索前置 / 公平推理",禁止临时机械降神。</p>
 */
@Component
public class MysteryPlotSkill implements Skill {

    @Override
    public String id() {
        return "mystery-plot";
    }

    @Override
    public String name() {
        return "推理布局";
    }

    @Override
    public String description() {
        return "推理题材:线索前置、公平推理、严守伏笔与反转的因果链";
    }

    @Override
    public List<String> triggers() {
        return List.of("推理", "案件", "凶手", "侦探", "密室", "不在场证明", "线索", "嫌疑人", "刑警", "尸体");
    }

    @Override
    public String promptSuffix() {
        return """
                推理题材写作铁律:
                1. 关键线索必须在揭秘前至少出现一次(可伪装成闲笔),禁止机械降神;
                2. 凶手 / 真相确定后,倒推检查每章是否留下足够伏笔,不留下"作者临时编出来"的痕迹;
                3. 嫌疑人至少 3 个有动机,真凶与其他嫌疑人动机均需合理;
                4. 不在场证明 / 凶器 / 死因 三要素必须明确,且其中至少一项被刻意误导;
                5. 侦探角色的推理链要可被读者复现,信息差公平;
                6. 章节结尾优先用"新线索出现 / 旧假设推翻"作为钩子,而非情绪钩。
                """;
    }

    @Override
    public List<String> toolWhitelist() {
        return List.of("queryCharacter", "queryTimeline", "countWords");
    }

    @Override
    public int priority() {
        return 12;
    }
}
