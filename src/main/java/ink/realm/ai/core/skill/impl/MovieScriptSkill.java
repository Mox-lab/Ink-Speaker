package ink.realm.ai.core.skill.impl;

import ink.realm.ai.core.skill.Skill;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 电影剧本风格写作技能。
 * <p>当大纲/主题包含"电影、剧本、分镜、镜头、蒙太奇、长镜头、对白"等关键词时激活。</p>
 *
 * <p>核心价值:用电影语言写作——镜头切换、画面构图、声画对位,
 * 适合剧本创作或追求影像化阅读体验的小说。</p>
 *
 * <p>主要适用:</p>
 * <ul>
 *   <li>电影剧本(类型片:犯罪 / 悬疑 / 文艺 / 科幻)</li>
 *   <li>影像化小说(强调画面感、留白、声画对位)</li>
 *   <li>短片 / 网剧脚本</li>
 * </ul>
 */
@Component
public class MovieScriptSkill implements Skill {

    @Override
    public String id() {
        return "movie-script";
    }

    @Override
    public String name() {
        return "电影剧本";
    }

    @Override
    public String description() {
        return "电影剧本:镜头语言、场景切换、对白克制、声画对位";
    }

    @Override
    public List<String> triggers() {
        return List.of("电影", "剧本", "分镜", "镜头", "蒙太奇", "长镜头", "对白", "场景标题",
                "外景", "内景", "淡入", "淡出", "切至", "推镜", "摇镜", "景别");
    }

    @Override
    public String promptSuffix() {
        return """
                电影剧本写作要点:
                1. 场景标题:每场以"INT./EXT. 地点 - 时间"格式起头,场景内禁止地点跳跃;
                2. 镜头语言:用文字暗示镜头(特写 / 全景 / 推 / 摇 / 跟),但不写成导演指令,而是融入叙述;
                3. 动作描写:用现在时态、短句、动词驱动,避免心理描写,情绪通过动作与环境外化;
                4. 对白克制:每句对白不超过 2 行,留白多于直说,潜台词优先于台词;
                5. 声画对位:至少一处"画面与声音错位"或"画外音"处理,营造影像感;
                6. 蒙太奇:转场用"切至 / 淡出 / 叠化"等术语,且每转场必须有情绪或时间跳跃的合理性;
                7. 节奏:每页(约 1 分钟)至少一个信息点或情绪转折,避免空镜堆砌;
                8. 结尾:每场以"动作收尾"或"对白留白"结束,禁止旁白解释。
                """;
    }

    @Override
    public List<String> toolWhitelist() {
        return List.of("queryCharacter", "queryWorldSetting", "queryTimeline", "countWords");
    }

    @Override
    public int priority() {
        return 8;
    }
}
