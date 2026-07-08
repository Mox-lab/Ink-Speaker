package com.ink.speaker.ai.core.tool.impl;

import com.ink.speaker.ai.core.tool.AiTool;
import com.ink.speaker.common.NovelContext;
import com.ink.speaker.config.cache.ToolCacheConfig;
import com.ink.speaker.novel.mapper.NovelChapterTimelineMapper;
import com.ink.speaker.novel.domain.entity.NovelChapterTimeline;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 工具:查询剧情时间线。
 * <p>查询已发生的剧情节点,避免剧情穿帮、时间线冲突。</p>
 * <p>novelId 来自 {@link NovelContext}。</p>
 *
 * <p>第 6 阶段(L1 缓存):同一章节内 LLM 常对同一关键词多次调用,
 * 走 {@code toolTimeline} 缓存(5min TTL)消除重复 DB 查询。
 * 兜底分支(列出全部章节)也走缓存,因为该查询成本最高。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimelineQueryTool implements AiTool {

    private final NovelChapterTimelineMapper timelineDao;

    @Tool(name = "queryTimeline", value = {
            "查询已发生的剧情节点。当需要回顾前情、衔接前后章节、避免剧情冲突时调用此工具。"})
    @Cacheable(cacheManager = ToolCacheConfig.TOOL_CACHE_MANAGER,
            value = "toolTimeline",
            key = "(T(com.ink.speaker.common.NovelContext).getNovelId() ?: 0) + ':' + (#keyword ?: '')",
            unless = "#result == null")
    public String queryTimeline(
            @P("章节序号或关键词,例如 第3章、云陵城相遇") String keyword) {
        Long novelId = NovelContext.requireNovelId();
        log.info("[Tool] queryTimeline novelId={} keyword={}", novelId, keyword);

        Integer chapterNo = parseChapterNo(keyword);
        if (chapterNo != null) {
            Optional<NovelChapterTimeline> node = timelineDao.findByNovelIdAndChapterNo(novelId, chapterNo);
            if (node.isPresent()) {
                return format(node.get());
            }
        }

        List<NovelChapterTimeline> fuzzy = timelineDao.searchByTitleOrSummary(novelId, keyword, keyword);
        if (!fuzzy.isEmpty()) {
            StringBuilder sb = new StringBuilder("匹配到多条剧情:\n");
            fuzzy.forEach(n -> sb.append(format(n)).append("\n"));
            return sb.toString();
        }

        List<NovelChapterTimeline> recent = timelineDao.listByNovelIdOrderByChapterNoAsc(novelId);
        if (recent.isEmpty()) {
            return String.format("时间线中未找到 '%s' 对应节点,且数据库尚无任何章节记录", keyword);
        }
        StringBuilder sb = new StringBuilder(String.format(
                "未精确匹配 '%s',以下是已完成的全部章节供参考:%n", keyword));
        recent.forEach(n -> sb.append(format(n)).append("\n"));
        return sb.toString();
    }

    private String format(NovelChapterTimeline t) {
        return String.format("【第%d章 %s】 %s",
                t.getChapterNo(),
                t.getTitle() == null ? "" : t.getTitle(),
                t.getSummary());
    }

    private Integer parseChapterNo(String input) {
        if (input == null) {
            return null;
        }
        String digits = input.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
