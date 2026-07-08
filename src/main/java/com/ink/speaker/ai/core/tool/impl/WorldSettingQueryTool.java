package com.ink.speaker.ai.core.tool.impl;

import com.ink.speaker.ai.core.tool.AiTool;
import com.ink.speaker.common.NovelContext;
import com.ink.speaker.config.cache.ToolCacheConfig;
import com.ink.speaker.novel.mapper.NovelWorldSettingMapper;
import com.ink.speaker.novel.domain.entity.NovelWorldSetting;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 工具:查询世界观设定。
 * <p>查询地理/势力/历史/武学体系等,避免设定矛盾。</p>
 * <p>novelId 来自 {@link NovelContext}。</p>
 *
 * <p>第 6 阶段(L1 缓存):同一章节内 LLM 常对同一关键词多次调用,
 * 走 {@code toolWorldSetting} 缓存(5min TTL)消除重复 DB 查询。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorldSettingQueryTool implements AiTool {

    private final NovelWorldSettingMapper worldSettingDao;

    @Tool(name = "queryWorldSetting", value = {
            "查询世界观设定关键词(地理/势力/历史/武学体系等)。当描写某个地点、势力、规则时调用此工具确认设定。"})
    @Cacheable(cacheManager = ToolCacheConfig.TOOL_CACHE_MANAGER,
            value = "toolWorldSetting",
            key = "(T(com.ink.speaker.common.NovelContext).getNovelId() ?: 0) + ':' + (#keyword ?: '')",
            unless = "#result == null || #result.startsWith('世界观库中未找到')")
    public String queryWorldSetting(
            @P("设定关键词,例如 青州、听潮阁、武学品阶") String keyword) {
        Long novelId = NovelContext.requireNovelId();
        log.info("[Tool] queryWorldSetting novelId={} keyword={}", novelId, keyword);

        Optional<NovelWorldSetting> exact = worldSettingDao.findByNovelIdAndKeyword(novelId, keyword);
        if (exact.isPresent()) {
            return format(exact.get());
        }

        List<NovelWorldSetting> fuzzy = worldSettingDao.searchByNovelIdAndKeywordContaining(novelId, keyword);
        if (!fuzzy.isEmpty()) {
            StringBuilder sb = new StringBuilder("匹配到多条设定:\n");
            fuzzy.forEach(s -> sb.append(format(s)).append("\n"));
            return sb.toString();
        }

        return String.format("世界观库中未找到 '%s' 相关设定,可参考同类作品自由发挥", keyword);
    }

    private String format(NovelWorldSetting s) {
        return String.format("【%s 的世界观设定】(分类:%s) %s",
                s.getKeyword(),
                s.getCategory() == null ? "未分类" : s.getCategory(),
                s.getDescription());
    }
}
