package ink.realm.ai.core.skill.impl;

import ink.realm.ai.core.skill.Skill;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 小说通用叙事技能。
 * <p>当大纲/主题包含"长篇小说、网络小说、章节、连载、主角、配角"等关键词时激活。</p>
 *
 * <p>核心价值:强化小说的连载节奏、章末钩子、人物弧光与世界观一致性,
 * 与默认通用 Skill 区分:小说 Skill 聚焦"长篇叙事结构",而非单章写作。</p>
 *
 * <p>主要适用:</p>
 * <ul>
 *   <li>网络小说连载(修仙 / 都市 / 历史 / 推理等长篇)</li>
 *   <li>传统长篇小说(严肃文学、类型小说)</li>
 *   <li>多视角、多线叙事的中长篇</li>
 * </ul>
 */
@Component
public class NovelWritingSkill implements Skill {

    @Override
    public String id() {
        return "novel-writing";
    }

    @Override
    public String name() {
        return "小说叙事";
    }

    @Override
    public String description() {
        return "长篇小说:章节钩子、人物弧光、多线叙事、世界观一致性";
    }

    @Override
    public List<String> triggers() {
        return List.of("小说", "长篇", "连载", "网文", "网络小说", "章节", "主角", "配角", "卷一", "卷二");
    }

    @Override
    public String promptSuffix() {
        return """
                长篇小说写作要点:
                1. 章节结构:开篇 1-2 段必须有钩子(悬念 / 冲突 / 反差),章尾必须留情绪或情节悬念,禁止"今天就到这里"式收尾;
                2. 人物弧光:每章主角至少有一个微小但可感知的变化(认知 / 关系 / 资源),避免章节内人物静止;
                3. 多线叙事:副线每隔 2-3 章必须显式推进一次,避免读者遗忘;
                4. 设定一致性:涉及具体人物 / 地点 / 势力 / 时间线时,优先调用工具核对,禁止凭记忆杜撰;
                5. 字数控制:目标字数浮动 ±10%,达不到时说明原因并补足;
                6. 节奏:对话与叙述交替,避免大段说明性文字;段落 3-5 行,适合手机阅读;
                7. 视角统一:同一场景内禁止随意切换 POV,切换需用空行 + 章节分节符明确标识。
                """;
    }

    @Override
    public List<String> toolWhitelist() {
        return List.of("queryCharacter", "queryWorldSetting", "queryTimeline", "expandScene", "countWords");
    }

    @Override
    public int priority() {
        return 5;
    }
}
