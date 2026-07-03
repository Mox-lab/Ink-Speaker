package com.novel.forge.tools;

import com.novel.forge.entity.NovelCharacter;
import com.novel.forge.entity.NovelChapterTimeline;
import com.novel.forge.entity.NovelWorldSetting;
import com.novel.forge.repository.CharacterRepository;
import com.novel.forge.repository.ChapterTimelineRepository;
import com.novel.forge.repository.WorldSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 小说创作工具集(Agent 的"手和眼")。
 * <p>LLM 通过调用这些工具来访问数据库中的设定、计数、扩写场景。
 * 所有查询走 PostgreSQL,重启不丢失,支持多本小说通过 novelId 隔离。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NovelTools {

    private final CharacterRepository characterRepo;        // 人物档案表
    private final WorldSettingRepository worldRepo;          // 世界观设定表
    private final ChapterTimelineRepository timelineRepo;    // 章节时间线表

    @Value("${novel.current-id:1}")
    private Long novelId;                                    // 当前小说 ID(预留多本小说切换)

    /**
     * 工具1: 查询人物档案。
     * <p>LLM 写到某个人物时调用,确保人设一致。</p>
     *
     * @param name 人物姓名(林晚/苏砚/赵九)
     * @return 人物档案字符串;未找到时返回提示
     */
    @dev.langchain4j.agent.tool.Tool(name = "queryCharacter",
            value = {"根据人物姓名查询其档案(年龄/性格/外貌/武器/背景)。当需要描写某个角色的言行、确保人设不崩塌时调用此工具。"})
    public String queryCharacter(
            @dev.langchain4j.agent.tool.P("人物姓名,例如 林晚、苏砚、赵九") String name) {
        log.info("[Tool] 调用 queryCharacter, novelId={}, name={}", novelId, name);  // 记录调用日志

        // 先精确匹配,再模糊匹配;兼容 LLM 传入 "林晚姑娘" 这类带后缀的输入
        Optional<NovelCharacter> exact = characterRepo.findByNovelIdAndName(novelId, name);
        if (exact.isPresent()) {                                              // 精确命中
            return formatCharacter(exact.get());
        }

        List<NovelCharacter> fuzzy = characterRepo.findByNovelIdAndNameContaining(novelId, name);
        if (!fuzzy.isEmpty()) {                                               // 模糊命中取第一条
            return formatCharacter(fuzzy.get(0)) + "\n(模糊匹配结果,如非目标人物请用更精确的姓名重试)";
        }

        return String.format("人物档案库中未找到 '%s',可自行创作但要保持前后一致", name);  // 双重未命中
    }

    /**
     * 工具2: 查询世界观设定。
     * <p>查询地理/势力/历史/武学体系等,避免设定矛盾。</p>
     *
     * @param keyword 设定关键词(青州/听潮阁/武学品阶)
     * @return 设定文本;未找到时返回自由发挥提示
     */
    @dev.langchain4j.agent.tool.Tool(name = "queryWorldSetting",
            value = {"查询世界观设定关键词(地理/势力/历史/武学体系等)。当描写某个地点、势力、规则时调用此工具确认设定。"})
    public String queryWorldSetting(
            @dev.langchain4j.agent.tool.P("设定关键词,例如 青州、听潮阁、武学品阶") String keyword) {
        log.info("[Tool] 调用 queryWorldSetting, novelId={}, keyword={}", novelId, keyword);

        // 先精确匹配 keyword,再模糊匹配
        Optional<NovelWorldSetting> exact = worldRepo.findByNovelIdAndKeyword(novelId, keyword);
        if (exact.isPresent()) {
            return formatWorldSetting(exact.get());
        }

        List<NovelWorldSetting> fuzzy = worldRepo.findByNovelIdAndKeywordContaining(novelId, keyword);
        if (!fuzzy.isEmpty()) {
            StringBuilder sb = new StringBuilder("匹配到多条设定:\n");
            fuzzy.forEach(s -> sb.append(formatWorldSetting(s)).append("\n"));
            return sb.toString();
        }

        return String.format("世界观库中未找到 '%s' 相关设定,可参考同类作品自由发挥", keyword);
    }

    /**
     * 工具3: 查询剧情时间线。
     * <p>查询已发生的剧情节点,避免剧情穿帮、时间线冲突。</p>
     *
     * @param keyword 章节序号或关键词(第3章/云陵城相遇)
     * @return 剧情节点文本;未找到时返回自由衔接提示
     */
    @dev.langchain4j.agent.tool.Tool(name = "queryTimeline",
            value = {"查询已发生的剧情节点。当需要回顾前情、衔接前后章节、避免剧情冲突时调用此工具。"})
    public String queryTimeline(
            @dev.langchain4j.agent.tool.P("章节序号或关键词,例如 第3章、云陵城相遇") String keyword) {
        log.info("[Tool] 调用 queryTimeline, novelId={}, keyword={}", novelId, keyword);

        // 尝试解析为章节序号(支持 "第3章" / "3" 两种格式)
        Integer chapterNo = parseChapterNo(keyword);
        if (chapterNo != null) {                                              // 输入是章号
            Optional<NovelChapterTimeline> node = timelineRepo.findByNovelIdAndChapterNo(novelId, chapterNo);
            if (node.isPresent()) {
                return formatTimeline(node.get());
            }
        }

        // 否则按标题/摘要模糊匹配
        List<NovelChapterTimeline> fuzzy = timelineRepo
                .findByNovelIdAndTitleContainingOrSummaryContaining(novelId, keyword, keyword);
        if (!fuzzy.isEmpty()) {
            StringBuilder sb = new StringBuilder("匹配到多条剧情:\n");
            fuzzy.forEach(n -> sb.append(formatTimeline(n)).append("\n"));
            return sb.toString();
        }

        // 都没命中,返回最近 3 章作为参考
        List<NovelChapterTimeline> recent = timelineRepo.findByNovelIdOrderByChapterNoAsc(novelId);
        if (recent.isEmpty()) {
            return String.format("时间线中未找到 '%s' 对应节点,且数据库尚无任何章节记录", keyword);
        }
        StringBuilder sb = new StringBuilder(String.format(
                "未精确匹配 '%s',以下是已完成的全部章节供参考:\n", keyword));
        recent.forEach(n -> sb.append(formatTimeline(n)).append("\n"));
        return sb.toString();
    }

    /**
     * 工具4: 字数统计。
     * <p>LLM 数不清字数,通过工具精确统计,满足"3000 字章节"这类硬要求。</p>
     *
     * @param text 待统计的文本
     * @return 字数统计字符串
     */
    @dev.langchain4j.agent.tool.Tool(name = "countWords",
            value = {"统计给定文本的字符数(含标点)。当需要确认章节字数、检查是否达到目标长度时调用此工具。"})
    public String countWords(
            @dev.langchain4j.agent.tool.P("待统计的文本内容") String text) {
        log.info("[Tool] 调用 countWords, 长度={}", text == null ? 0 : text.length());
        if (text == null || text.isEmpty()) {                                 // 空文本直接返回 0
            return "字数: 0";
        }
        int chars = text.length();                                            // 总字符数(含空白与标点)
        int charsNoSpace = text.replaceAll("\\s", "").length();               // 去空白后的字符数
        return String.format("字数统计: 总字符 %d, 去空白 %d", chars, charsNoSpace);
    }

    /**
     * 工具5: 场景扩写建议。
     * <p>给定简短场景,返回五感维度的扩写提示,帮助 LLM 在卡文时找到方向。</p>
     *
     * @param scene 简短场景描述(如 "雨夜 林晚在码头等苏砚")
     * @return 多行扩写建议
     */
    @dev.langchain4j.agent.tool.Tool(name = "expandScene",
            value = {"为简短场景提供感官维度的扩写建议。当用户要求扩写、需要丰富细节、卡文时调用此工具。"})
    public String expandScene(
            @dev.langchain4j.agent.tool.P("简短场景描述,例如 雨夜 林晚在码头等苏砚") String scene) {
        log.info("[Tool] 调用 expandScene, scene={}", scene);
        String[] senses = {"视觉", "听觉", "嗅觉", "触觉", "情绪"};             // 五感+情绪维度
        String[] hints = {                                                    // 每个维度的引导问题
                "雨幕如何拍打青石板?灯火如何映在水面?",
                "雨声是否盖住脚步?远处有没有更鼓?",
                "潮湿的江风带着什么气味?鱼腥还是泥土?",
                "她的衣袖湿透,贴在皮肤上是冷是黏?",
                "等待的焦灼与一丝不敢承认的期待如何交织?"
        };
        StringBuilder sb = new StringBuilder();                               // 拼接最终建议
        sb.append("【").append(scene).append(" 的扩写建议】\n");
        for (int i = 0; i < senses.length; i++) {                             // 逐维度列出
            sb.append("- ").append(senses[i]).append(": ").append(hints[i]).append("\n");
        }
        return sb.toString();
    }

    // ============================================================
    // 内部辅助方法
    // ============================================================

    /**
     * 把人物实体格式化为 LLM 易读的字符串。
     */
    private String formatCharacter(NovelCharacter c) {
        return String.format("【%s 的人物档案】性别:%s, 年龄:%s, 性格:%s, 武器:%s, 背景:%s",
                c.getName(),
                c.getGender() == null ? "未设定" : c.getGender(),
                c.getAge() == null ? "未设定" : c.getAge(),
                c.getPersonality() == null ? "未设定" : c.getPersonality(),
                c.getWeapon() == null ? "无" : c.getWeapon(),
                c.getBackground() == null ? "未设定" : c.getBackground());
    }

    /**
     * 把世界观设定实体格式化。
     */
    private String formatWorldSetting(NovelWorldSetting s) {
        return String.format("【%s 的世界观设定】(分类:%s) %s",
                s.getKeyword(),
                s.getCategory() == null ? "未分类" : s.getCategory(),
                s.getDescription());
    }

    /**
     * 把时间线实体格式化。
     */
    private String formatTimeline(NovelChapterTimeline t) {
        return String.format("【第%d章 %s】 %s",
                t.getChapterNo(),
                t.getTitle() == null ? "" : t.getTitle(),
                t.getSummary());
    }

    /**
     * 解析章节序号,支持 "第3章" / "第三章" / "3" 三种输入。
     *
     * @param input 用户输入
     * @return 章节序号;无法解析时返回 null
     */
    private Integer parseChapterNo(String input) {
        if (input == null) return null;
        String digits = input.replaceAll("[^0-9]", "");                        // 去掉所有非数字字符
        if (digits.isEmpty()) return null;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
