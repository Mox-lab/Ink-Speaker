package ink.realm.ai.core.skill.impl;

import ink.realm.ai.core.skill.Skill;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 动漫风格写作技能。
 * <p>当大纲/主题包含"动漫、番剧、热血、中二、机甲、魔法少女、异世界"等关键词时激活。</p>
 *
 * <p>核心价值:捕捉日系动漫的视觉化叙事节奏——夸张的表情动作、台词驱动、
 * 名场面构图,适合改编脚本或动漫风格小说创作。</p>
 *
 * <p>主要适用:</p>
 * <ul>
 *   <li>热血少年向(战斗成长、羁绊、爆种)</li>
 *   <li>异世界 / 转生题材(金手指、徒弟收编、基建)</li>
 *   <li>魔法少女 / 机甲 / 中二奇幻</li>
 *   <li>轻小说风格的对话密集型叙事</li>
 * </ul>
 */
@Component
public class AnimeStyleSkill implements Skill {

    @Override
    public String id() {
        return "anime-style";
    }

    @Override
    public String name() {
        return "动漫风格";
    }

    @Override
    public String description() {
        return "动漫风格:台词驱动、名场面构图、热血爆种、视觉化动作描写";
    }

    @Override
    public List<String> triggers() {
        return List.of("动漫", "番剧", "热血", "中二", "机甲", "魔法少女", "异世界", "转生", "轻小说",
                "羁绊", "爆种", "招式名", "必杀技", "卡通", "动画");
    }

    @Override
    public String promptSuffix() {
        return """
                动漫风格写作要点:
                1. 视觉化:每个关键场景都要有"名场面构图"——光线、姿态、表情、招牌台词四要素至少三要素齐全;
                2. 台词驱动:对话占比 ≥40%,台词要短促有力、有节奏感,允许中二/夸张措辞("这就是我的觉悟!"等);
                3. 招式与必杀技:重要招式必须有"招式名 + 吟唱 / 解说",且解说不超过两句,避免冗长;
                4. 羁绊与爆种:关键时刻允许"友情 / 觉悟 / 回忆"触发实力爆发,但每章最多一次,且需前置伏笔;
                5. 节奏单位:以"幕"为单位推进,每幕 = 一个冲突起承转合,章末留"接下来该如何是好?"式悬念;
                6. 表情与动作:用夸张的肢体语言与面部表情传达情绪(挑眉、咬唇、握拳、风衣飘动等),避免纯心理描写;
                7. 反派魅力:反派要有"信念 / 美学 / 过去"三选一,避免脸谱化;
                8. 中二词汇适度:特殊名词用全角引号或片假名风格标注,但同一章不超过 5 个,避免阅读疲劳。
                """;
    }

    @Override
    public List<String> toolWhitelist() {
        return List.of("queryCharacter", "queryWorldSetting", "queryTimeline", "expandScene", "countWords");
    }

    @Override
    public int priority() {
        return 8;
    }
}
