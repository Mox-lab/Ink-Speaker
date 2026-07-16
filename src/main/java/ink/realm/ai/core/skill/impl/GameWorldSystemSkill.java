package ink.realm.ai.core.skill.impl;

import ink.realm.ai.core.skill.Skill;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 游戏世界 / 系统流写作技能。
 * <p>当大纲/主题包含"游戏、副本、系统、属性面板、技能树、NPC、boss、存档、读档、无限流"
 * 等关键词时激活。</p>
 *
 * <p>核心价值:处理游戏化世界的写作难题——
 * 数值化系统的克制呈现、副本节奏、NPC 与玩家的关系、死亡与复活的张力,
 * 避免"属性面板刷屏"和"死亡失去意义"两大通病。</p>
 *
 * <p>主要适用:</p>
 * <ul>
 *   <li>系统流(主角绑定签到 / 任务 / 抽奖系统)</li>
 *   <li>无限流(穿梭多个副本世界)</li>
 *   <li>VR 游戏 / 全息网游</li>
 *   <li>异世界带游戏化 UI(属性 / 技能树 / 任务)</li>
 *   <li>穿越进游戏世界(单机 / 网游)</li>
 * </ul>
 */
@Component
public class GameWorldSystemSkill implements Skill {

    @Override
    public String id() {
        return "game-world-system";
    }

    @Override
    public String name() {
        return "游戏世界";
    }

    @Override
    public String description() {
        return "游戏/系统/无限流:数值克制、副本节奏、NPC 关系、死亡与复活张力";
    }

    @Override
    public List<String> triggers() {
        return List.of("游戏", "副本", "系统", "属性面板", "技能树", "NPC", "boss", "Boss", "BOSS",
                "存档", "读档", "无限流", "轮回者", "任务", "签到", "抽奖", "升级", "经验值",
                "玩家", "网游", "单机", "全息", "VR", "副本通关", "复活", "死亡回放");
    }

    @Override
    public String promptSuffix() {
        return """
                游戏世界 / 系统流写作要点:
                1. 数值克制:属性面板 / 系统提示每章最多出现 2 次,且必须有信息增量(新技能 / 新任务 / 关键 warning),
                   禁止用面板凑字数;面板内容用 【系统】 或 「」 包裹,与正文视觉区隔;
                2. 系统人格化:系统 / 主神 / GM 要有"性格 + 限制 + 隐藏目的"三要素,避免沦为工具人;
                3. 副本节奏:单副本结构按"入场侦察 → 规则摸清 → 关键抉择 → Boss 战 → 结算"五段,
                   每段不少于本章 1/5 篇幅,禁止跳跃推进;
                4. 死亡张力:死亡必须有真实代价(经验清零 / 道具掉落 / 副本冷却 / NPC 死亡不可逆),
                   禁止"无限白嫖复活",关键 NPC 死亡要留给主角情绪余震;
                5. NPC 维度:重要 NPC 至少有"表层功能(商人 / 引导者)+ 隐藏身份 / 故事"双层设计,
                   避免工具人化;
                6. 玩家 vs 原住民:玩家的"复活 / 读档"特权必须引发原住民的情感冲击(恐惧 / 崇拜 / 厌恶),
                   作为核心戏剧张力;
                7. 任务设计:主线任务 + 隐藏任务 + 阵营任务至少并行 2 条,隐藏任务触发要有伏笔;
                8. 资源管理:金币 / 道具 / 技能点要显式稀缺,关键消费节点要给出权衡(买 A 还是 B);
                9. 副本世界规则:每个副本世界要有独立规则(魔法体系 / 科技水平 / 禁忌),
                   禁止跨副本无差别套用同一套战斗方式;
                10. 元游戏克制:主角"知道这是游戏"的自觉要克制表达,每章最多 1 处,避免破坏沉浸感;
                11. 涉及具体 NPC / 副本设定时,优先调用 queryCharacter / queryWorldSetting / queryTimeline 核对。
                """;
    }

    @Override
    public List<String> toolWhitelist() {
        return List.of("queryCharacter", "queryWorldSetting", "queryTimeline", "expandScene",
                "searchExternalKnowledge", "countWords");
    }

    @Override
    public int priority() {
        return 9;
    }
}
