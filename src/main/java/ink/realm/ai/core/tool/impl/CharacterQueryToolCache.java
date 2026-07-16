package ink.realm.ai.core.tool.impl;

import ink.realm.common.context.NovelContext;
import ink.realm.config.cache.ToolCacheConfig;
import ink.realm.novel.domain.entity.NovelCharacter;
import ink.realm.novel.mapper.NovelCharacterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 人物档案查询缓存层。
 * <p>将缓存注解与 DB 查询下沉到本独立 Bean,使 {@link CharacterQueryTool}
 * 不再被 Spring AOP 代理,从而保留其方法上的 LangChain4j {@code @Tool}
 * 注解(详见 {@link CharacterQueryTool} 类注释)。</p>
 *
 * <p>novelId 来自 {@link NovelContext}(由 {@code NovelContextFilter} 从
 * X-Novel-Id 头注入)。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CharacterQueryToolCache {

    private final NovelCharacterMapper characterDao;

    /** 查询人物档案(走 toolCharacter 缓存)。 */
    @Cacheable(cacheManager = ToolCacheConfig.TOOL_CACHE_MANAGER,
            value = "toolCharacter",
            key = "(T(ink.realm.common.context.NovelContext).getNovelId() ?: 0) + ':' + (#name ?: '')",
            unless = "#result == null || #result.startsWith('人物档案库中未找到')")
    public String query(String name) {
        Long novelId = NovelContext.requireNovelId();
        log.info("[Tool] queryCharacter novelId={} name={}", novelId, name);

        return characterDao.findByNovelIdAndName(novelId, name)
                .map(this::format)
                .orElseGet(() -> {
                    List<NovelCharacter> fuzzy = characterDao.searchByNovelIdAndNameContaining(novelId, name);
                    if (!fuzzy.isEmpty()) {
                        return format(fuzzy.get(0)) + "\n(模糊匹配结果,如非目标人物请用更精确的姓名重试)";
                    }
                    return String.format("人物档案库中未找到 '%s',可自行创作但要保持前后一致", name);
                });
    }

    private String format(NovelCharacter c) {
        return String.format("【%s 的人物档案】性别:%s, 年龄:%s, 性格:%s, 武器:%s, 背景:%s",
                c.getName(),
                c.getGender() == null ? "未设定" : c.getGender(),
                c.getAge() == null ? "未设定" : c.getAge(),
                c.getPersonality() == null ? "未设定" : c.getPersonality(),
                c.getWeapon() == null ? "无" : c.getWeapon(),
                c.getBackground() == null ? "未设定" : c.getBackground());
    }
}
