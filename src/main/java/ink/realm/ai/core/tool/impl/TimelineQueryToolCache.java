package ink.realm.ai.core.tool.impl;

import ink.realm.common.context.NovelContext;
import ink.realm.config.cache.ToolCacheConfig;
import ink.realm.novel.domain.entity.NovelChapterTimeline;
import ink.realm.novel.mapper.NovelChapterTimelineMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 剧情时间线查询缓存层。
 * <p>将缓存注解与 DB 查询下沉到本独立 Bean,使 {@link TimelineQueryTool}
 * 不再被 Spring AOP 代理,从而保留其方法上的 LangChain4j {@code @Tool}
 * 注解(详见 {@link TimelineQueryTool} 类注释)。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimelineQueryToolCache {

    private final NovelChapterTimelineMapper timelineDao;

    /** 查询剧情时间线(走 toolTimeline 缓存)。 */
    @Cacheable(cacheManager = ToolCacheConfig.TOOL_CACHE_MANAGER,
            value = "toolTimeline",
            key = "(T(ink.realm.common.context.NovelContext).getNovelId() ?: 0) + ':' + (#keyword ?: '')",
            unless = "#result == null")
    public String query(String keyword) {
        Long novelId = NovelContext.requireNovelId();
        log.info("[Tool] queryTimeline novelId={} keyword={}", novelId, keyword);

        Integer chapterNo = parseChapterNo(keyword);
        if (chapterNo != null) {
            return timelineDao.findByNovelIdAndChapterNo(novelId, chapterNo)
                    .map(this::format)
                    .orElseGet(() -> buildTimelineFallback(novelId, keyword));
        }
        return buildTimelineFallback(novelId, keyword);
    }

    /**
     * 时间线兜底查询:精确章节号未命中时,依次尝试标题/摘要模糊匹配、最近章节列表。
     */
    private String buildTimelineFallback(Long novelId, String keyword) {
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
