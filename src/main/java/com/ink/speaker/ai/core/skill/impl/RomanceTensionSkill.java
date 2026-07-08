package com.ink.speaker.ai.core.skill.impl;

import com.ink.speaker.ai.core.skill.Skill;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 言情张力技能。
 * <p>当主题包含"恋爱、暗恋、青梅竹马、心动、告白、纠葛"等关键词时激活。</p>
 * <p>核心价值:注重情感节奏与张力,反对直白表白与扁平甜宠。</p>
 */
@Component
public class RomanceTensionSkill implements Skill {

    @Override
    public String id() {
        return "romance-tension";
    }

    @Override
    public String name() {
        return "言情张力";
    }

    @Override
    public String description() {
        return "言情题材:情感节奏、CP 张力、推拉与留白,拒绝扁平甜宠";
    }

    @Override
    public List<String> triggers() {
        return List.of("言情", "恋爱", "暗恋", "心动", "告白", "青梅竹马", "情敌", "前任", "CP", "白月光", "纠葛");
    }

    @Override
    public String promptSuffix() {
        return """
                言情题材写作要点:
                1. 情感推进按"陌生 → 好感 → 试探 → 误解 → 表白"五段曲线,禁止跳级;
                2. 张力来源:细节留白(指尖轻触 / 目光停留)优于直接身体接触,前期尤为关键;
                3. 对话推拉:有"问而不答 / 答非所问 / 故意曲解"等高密度互动,避免一问一答平铺;
                4. 内心戏与外在表现错位(嘴上说不在意,动作出卖自己),制造反差;
                5. 严禁直白告白过早出现;情感高潮必须前置铺垫至少 3 章;
                6. 配角不能纯工具人,情敌 / 闺蜜也需有独立动机与性格。
                """;
    }

    @Override
    public int priority() {
        return 9;
    }
}
