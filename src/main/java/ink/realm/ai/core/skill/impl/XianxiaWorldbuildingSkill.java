package ink.realm.ai.core.skill.impl;

import ink.realm.ai.core.skill.Skill;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 修仙世界观构建技能。
 * <p>当大纲/主题包含"修炼、灵根、丹药、宗门、劫雷、元婴"等关键词时自动激活。</p>
 * <p>核心价值:让 AI 严格遵守"境界不可越级"等修仙题材铁律,
 * 调用 queryWorldSetting 工具核对功法/境界体系。</p>
 */
@Component
public class XianxiaWorldbuildingSkill implements Skill {

    @Override
    public String id() {
        return "xianxia-worldbuilding";
    }

    @Override
    public String name() {
        return "修仙世界观";
    }

    @Override
    public String description() {
        return "修仙题材:境界、灵根、丹药、宗门体系,严格遵守修仙铁律";
    }

    @Override
    public List<String> triggers() {
        return List.of("修仙", "修炼", "灵根", "境界", "元婴", "化神", "金丹", "筑基", "宗门", "劫雷", "功法", "丹方", "秘境");
    }

    @Override
    public String promptSuffix() {
        return """
                修仙题材写作铁律:
                1. 境界严格按从低到高:练气 → 筑基 → 金丹 → 元婴 → 化神 → 渡劫 → 大乘(若有自定义境界,以设定集为准);
                2. 越级战斗必须给出合理依据(法宝/伏笔/突破),不允许主角无理由越级碾压;
                3. 涉及具体境界、功法、丹药、灵根时,先调用 queryWorldSetting 核对,保持体系一致;
                4. 突破场景要有"灵气 / 天劫 / 心境"三要素中至少两项;
                5. 宗门 / 势力称呼要符合修仙世界习惯(道友、前辈、师叔、本座),避免出戏现代词。
                """;
    }

    @Override
    public List<String> toolWhitelist() {
        return List.of("queryCharacter", "queryWorldSetting", "queryTimeline", "countWords");
    }

    @Override
    public int priority() {
        return 10;
    }
}
