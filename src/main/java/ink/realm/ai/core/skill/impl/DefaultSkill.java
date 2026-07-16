package ink.realm.ai.core.skill.impl;

import ink.realm.ai.core.skill.Skill;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 默认通用写作技能。
 * <p>不绑定具体题材,作为 fallback。永远存在,且 id 固定为 {@code default}。</p>
 */
@Component
public class DefaultSkill implements Skill {

    @Override
    public String id() {
        return "default";
    }

    @Override
    public String name() {
        return "通用网文写作";
    }

    @Override
    public String description() {
        return "默认写作风格,平衡叙事、对话与画面感,适配大多数网文题材";
    }

    @Override
    public List<String> triggers() {
        return List.of();
    }

    @Override
    public String promptSuffix() {
        return """
                风格基线:
                - 叙述与对话交替推进,避免大段说明;
                - 适度心理描写,不要刻意渲染;
                - 章节有明确钩子与悬念收尾;
                - 用词简洁有力,避免堆砌辞藻。
                """;
    }

    @Override
    public int priority() {
        return -1;
    }
}
