package ink.realm.ai.core.skill.impl;

import ink.realm.ai.core.skill.Skill;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 武打 / 战斗场景调度技能。
 * <p>当主题包含"对决、剑气、招式、阵法、鏖战"等关键词时激活。</p>
 * <p>核心价值:战斗节奏有起承转合,招式有画面,避免一招制敌的扁平写法。</p>
 */
@Component
public class BattleSceneSkill implements Skill {

    @Override
    public String id() {
        return "battle-scene";
    }

    @Override
    public String name() {
        return "武打调度";
    }

    @Override
    public String description() {
        return "战斗场景:招式画面感、节奏起承转合、地形与配角的合理调度";
    }

    @Override
    public List<String> triggers() {
        return List.of("对决", "决斗", "剑气", "招式", "阵法", "鏖战", "杀招", "斗法", "比武", "血战", "突围");
    }

    @Override
    public String promptSuffix() {
        return """
                战斗场景写作要点:
                1. 结构按"对峙 → 试探 → 拉锯 → 转折 → 终局"五段,不允许一招制敌;
                2. 招式命名与所属流派一致(修仙用"剑诀 / 法印",武侠用"剑招 / 掌法",不要混用);
                3. 必须使用"环境 / 道具 / 配角"中至少两项作为变量,场景不能空荡;
                4. 人物受伤要有累积与影响(下一招变慢 / 喘息加深),不能伤完即愈;
                5. 关键反转要前置伏笔(法宝 / 弱点 / 同伴支援),且至少出现一次;
                6. 节奏感:短句写动作,长句写心理,环境描写插在动作间隙。
                """;
    }

    @Override
    public List<String> toolWhitelist() {
        return List.of("queryCharacter", "queryWorldSetting", "queryTimeline", "expandScene", "countWords");
    }

    @Override
    public int priority() {
        return 11;
    }
}
