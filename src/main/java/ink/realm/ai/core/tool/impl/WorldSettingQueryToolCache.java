package ink.realm.ai.core.tool.impl;

import ink.realm.common.context.NovelContext;
import ink.realm.config.cache.ToolCacheConfig;
import ink.realm.novel.domain.entity.NovelWorldSetting;
import ink.realm.novel.mapper.NovelWorldSettingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 世界观设定查询缓存层。
 * <p>将缓存注解与 DB 查询下沉到本独立 Bean,使 {@link WorldSettingQueryTool}
 * 不再被 Spring AOP 代理,从而保留其方法上的 LangChain4j {@code @Tool}
 * 注解(详见 {@link WorldSettingQueryTool} 类注释)。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorldSettingQueryToolCache {

    private final NovelWorldSettingMapper worldSettingDao;

    /** 查询世界观设定(走 toolWorldSetting 缓存)。 */
    @Cacheable(cacheManager = ToolCacheConfig.TOOL_CACHE_MANAGER,
            value = "toolWorldSetting",
            key = "(T(ink.realm.common.context.NovelContext).getNovelId() ?: 0) + ':' + (#keyword ?: '')",
            unless = "#result == null || #result.startsWith('世界观库中未找到')")
    public String query(String keyword) {
        Long novelId = NovelContext.requireNovelId();
        log.info("[Tool] queryWorldSetting novelId={} keyword={}", novelId, keyword);

        return worldSettingDao.findByNovelIdAndKeyword(novelId, keyword)
                .map(this::format)
                .orElseGet(() -> {
                    List<NovelWorldSetting> fuzzy = worldSettingDao.searchByNovelIdAndKeywordContaining(novelId, keyword);
                    if (!fuzzy.isEmpty()) {
                        StringBuilder sb = new StringBuilder("匹配到多条设定:\n");
                        fuzzy.forEach(s -> sb.append(format(s)).append("\n"));
                        return sb.toString();
                    }
                    return String.format("世界观库中未找到 '%s' 相关设定,可参考同类作品自由发挥", keyword);
                });
    }

    private String format(NovelWorldSetting s) {
        return String.format("【%s 的世界观设定】(分类:%s) %s",
                s.getKeyword(),
                s.getCategory() == null ? "未分类" : s.getCategory(),
                s.getDescription());
    }
}
