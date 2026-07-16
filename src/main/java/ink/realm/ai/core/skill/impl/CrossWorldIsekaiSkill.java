package ink.realm.ai.core.skill.impl;

import ink.realm.ai.core.skill.Skill;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 穿越 / 异世界流写作技能。
 * <p>当大纲/主题包含"穿越、异世界、穿书、穿剧、穿漫、轮回、重生、夺舍、附身"等关键词时激活。</p>
 *
 * <p>核心价值:处理"现代人穿越进虚构世界"题材的核心写作难题——
 * 主角的元知识优势如何克制使用、与原世界规则的碰撞、剧情走向的蝴蝶效应、
 * 以及避免"全知全能"导致的故事张力丧失。</p>
 *
 * <p>主要适用:</p>
 * <ul>
 *   <li>穿越到小说 / 电影 / 动漫 / 漫画 / 游戏 等已有虚构作品的世界</li>
 *   <li>穿越到架空异世界(修仙 / 西幻 / 末世 / 星际等)</li>
 *   <li>重生流(回到自己过去的某个时间点)</li>
 *   <li>轮回流 / 无限流前置(单次穿越)</li>
 * </ul>
 *
 * <p><b>与其他 Skill 的协作:</b>本 Skill 关注"穿越机制与元知识处理";
 * 若宿主世界有特定风格(如修仙 / 动漫 / 游戏),应同时叠加对应 Skill。
 * SkillRegistry 的优先级机制保证高 priority Skill 命中后不被覆盖,
 * 故本 Skill priority 设为 7,低于题材专用 Skill (8-11),让位给更具体的风格。</p>
 */
@Component
public class CrossWorldIsekaiSkill implements Skill {

    @Override
    public String id() {
        return "cross-world-isekai";
    }

    @Override
    public String name() {
        return "穿越异世界";
    }

    @Override
    public String description() {
        return "穿越/重生/轮回流:元知识克制、蝴蝶效应、规则碰撞、原著人物关系处理";
    }

    @Override
    public List<String> triggers() {
        return List.of("穿越", "异世界", "穿书", "穿剧", "穿漫", "穿游", "轮回", "重生", "夺舍",
                "附身", "魂穿", "胎穿", "身穿", "现代人", "回到过去", "回到", "前世", "今生",
                "原著", "剧情", "剧透", "已知未来");
    }

    @Override
    public String promptSuffix() {
        return """
                穿越 / 异世界流写作要点:
                1. 元知识克制:主角的"原著剧透"优势必须设置使用代价(被规则反噬 / 引发蝴蝶效应 / 暴露身份),
                   禁止无代价全知全能,每使用一次元知识至少触发一次不可控后果;
                2. 蝴蝶效应:主角介入后,原著后续剧情必须显式偏离,且偏离要有逻辑链(因 → 果),不可生硬回归原著;
                3. 规则碰撞:主角的现代思维与异世界规则至少产生 2 次冲突(道德 / 常识 / 法律),用作人物弧光驱动;
                4. 身份伪装:穿越者身份是核心悬念之一,前 20 章不主动暴露,暴露节点必须有强情绪冲击;
                5. 原著人物:对原著关键人物的刻画要"尊重原设定 + 注入新解读",禁止脸谱化或刻意洗白 / 抹黑;
                6. 金手指平衡:任何外挂(系统 / 神器 / 前世记忆)必须有冷却 / 代价 / 成长曲线,避免碾压式爽感;
                7. 时间锚点:明确交代穿越的时间节点(原著第几章 / 哪个事件前后),并在后续章节保持时间线一致;
                8. 情感纽带:主角必须与至少 1 位原住民建立深度情感(友情 / 爱情 / 师徒),作为"留下"的动机锚;
                9. 回归悬念:是否回归原世界作为长线悬念,前期不揭晓,但每 10 章至少推进一次相关伏笔;
                10. 涉及原著具体人物 / 设定 / 时间线时,优先调用 queryCharacter / queryWorldSetting / queryTimeline 核对。
                """;
    }

    @Override
    public List<String> toolWhitelist() {
        return List.of("queryCharacter", "queryWorldSetting", "queryTimeline", "expandScene",
                "searchExternalKnowledge", "countWords");
    }

    @Override
    public int priority() {
        return 7;
    }
}
