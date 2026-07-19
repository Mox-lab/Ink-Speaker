package ink.realm.ai.core.tool.impl;

import ink.realm.common.context.NovelContext;
import ink.realm.config.cache.ToolCacheConfig;
import ink.realm.novel.domain.entity.NovelWorldSetting;
import ink.realm.novel.mapper.NovelWorldSettingMapper;
import ink.realm.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 人物档案查询缓存层。
 * <p>将缓存注解与 DB 查询下沉到本独立 Bean,使 {@link CharacterQueryTool}
 * 不再被 Spring AOP 代理,从而保留其方法上的 LangChain4j {@code @Tool}
 * 注解(详见 {@link CharacterQueryTool} 类注释)。</p>
 *
 * <p>人物数据统一来源于设定集「人物」分类(novel_world_setting WHERE category='人物'),
 * description 内嵌 {@code {_struct:'character', ...}} 结构化 JSON,本类解析后格式化输出。</p>
 *
 * <p>novelId 来自 {@link NovelContext}(由 {@code NovelContextFilter} 从
 * X-Novel-Id 头注入)。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CharacterQueryToolCache {

    private final NovelWorldSettingMapper worldSettingDao;

    /** 查询人物档案(走 toolCharacter 缓存)。 */
    @Cacheable(cacheManager = ToolCacheConfig.TOOL_CACHE_MANAGER,
            value = "toolCharacter",
            key = "(T(ink.realm.common.context.NovelContext).getNovelId() ?: 0) + ':' + (#name ?: '')",
            unless = "#result == null || #result.startsWith('人物档案库中未找到')")
    public String query(String name) {
        Long novelId = NovelContext.requireNovelId();
        log.info("[Tool] queryCharacter novelId={} name={}", novelId, name);

        return worldSettingDao.findByNovelIdAndKeyword(novelId, name)
                .filter(CharacterQueryToolCache::isCharacter)
                .map(this::format)
                .orElseGet(() -> {
                    // 模糊匹配:在「人物」分类内,keyword 包含 name(忽略大小写)
                    List<NovelWorldSetting> fuzzy = worldSettingDao
                            .listByNovelIdAndCategory(novelId, "人物").stream()
                            .filter(s -> s.getKeyword() != null
                                    && s.getKeyword().toLowerCase().contains(name.toLowerCase()))
                            .toList();
                    if (!fuzzy.isEmpty()) {
                        return format(fuzzy.get(0)) + "\n(模糊匹配结果,如非目标人物请用更精确的姓名重试)";
                    }
                    return String.format("人物档案库中未找到 '%s',可自行创作但要保持前后一致", name);
                });
    }

    /** 是否为「人物」分类条目。 */
    private static boolean isCharacter(NovelWorldSetting s) {
        return "人物".equals(s.getCategory());
    }

    /** 把设定集人物条目格式化为可读档案文本。 */
    private String format(NovelWorldSetting s) {
        Map<String, Object> m = parseCharacterStruct(s.getDescription());
        String gender = str(m.get("gender"));
        String age = m.get("age") == null ? "未设定" : String.valueOf(m.get("age"));
        String personality = str(m.get("personality"));
        String weapon = str(m.get("weapon"));
        String background = str(m.get("background"));
        return String.format("【%s 的人物档案】性别:%s, 年龄:%s, 性格:%s, 武器:%s, 背景:%s",
                s.getKeyword(),
                gender.isEmpty() ? "未设定" : gender,
                age,
                personality.isEmpty() ? "未设定" : personality,
                weapon.isEmpty() ? "无" : weapon,
                background.isEmpty() ? "未设定" : background);
    }

    /** 解析 description 中的 character 结构,返回其 data 字段(去除 _struct/text)。 */
    private Map<String, Object> parseCharacterStruct(String desc) {
        if (desc == null || desc.isBlank()) {
            return Collections.emptyMap();
        }
        Map<String, Object> parsed = JsonUtil.parseMap(desc);
        if (parsed == null || !"character".equals(parsed.get("_struct"))) {
            return Collections.emptyMap();
        }
        parsed.remove("_struct");
        parsed.remove("text");
        return parsed;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
