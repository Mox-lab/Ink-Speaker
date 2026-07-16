package ink.realm.ai.core.skill.impl;

import ink.realm.ai.core.skill.Skill;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 漫画分镜风格写作技能。
 * <p>当大纲/主题包含"漫画、分镜、格子、对白框、拟声词、少年漫、少女漫"等关键词时激活。</p>
 *
 * <p>核心价值:用漫画分镜思维写作——每段文字对应一格画面,
 * 强调视觉冲击、对白框节奏、拟声词与画面切换,适合漫画脚本或漫画风小说。</p>
 *
 * <p>主要适用:</p>
 * <ul>
 *   <li>漫画脚本(少年漫 / 少女漫 / 青年漫)</li>
 *   <li>条漫 / 网漫脚本</li>
 *   <li>漫画风格小说(强视觉、强分镜感)</li>
 * </ul>
 */
@Component
public class MangaPanelSkill implements Skill {

    @Override
    public String id() {
        return "manga-panel";
    }

    @Override
    public String name() {
        return "漫画分镜";
    }

    @Override
    public String description() {
        return "漫画分镜:一格一画面、对白框节奏、拟声词、视觉冲击";
    }

    @Override
    public List<String> triggers() {
        return List.of("漫画", "分镜", "格子", "对白框", "拟声词", "少年漫", "少女漫", "青年漫",
                "条漫", "网漫", "四格", "跨页", "特效字", "集中线");
    }

    @Override
    public String promptSuffix() {
        return """
                漫画分镜写作要点:
                1. 一格一画面:每个自然段对应一格分镜,段末可用 【分镜:XXX】 标注(景别 / 视角 / 构图);
                2. 跨页与特写:重要情绪 / 反转用跨页或大特写,每章至少 1 处,且需前置铺垫;
                3. 对白框节奏:对白框大小 = 台词情绪强度;爆发用大号字体描写,耳语用小号字体描写;
                4. 拟声词:关键动作配拟声词(轰 / 唰 / 砰 / 滴答),每章不超过 5 处,避免视觉疲劳;
                5. 集中线与特效字:情绪爆发点用集中线 / 速度线 / 闪白等特效字标注,但要克制;
                6. 视角切换:同一场景视角至少切换 2 次(全景 → 中景 → 特写),避免镜头单调;
                7. 留白:跨页 / 半页留白用于情绪缓冲,禁止用文字填满;
                8. 节奏:对话密集用四格节奏(起承转合),动作场面用 3-4 格推进,避免一格塞过多信息。
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
