package com.ink.speaker.ai.core.tool.impl;

import com.ink.speaker.ai.core.tool.AiTool;
import com.ink.speaker.common.NovelContext;
import com.ink.speaker.config.cache.ToolCacheConfig;
import com.ink.speaker.novel.mapper.NovelCharacterMapper;
import com.ink.speaker.novel.domain.entity.NovelCharacter;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 工具:查询人物档案。
 * <p>LLM 写到某个人物时调用,确保人设一致。先精确匹配,再模糊匹配。</p>
 * <p>novelId 来自 {@link NovelContext}(由 {@code NovelContextFilter} 从 X-Novel-Id 头注入)。</p>
 *
 * <p>第 6 阶段(L1 缓存):同一章节内 LLM 常对同一人物多次调用,
 * 走 {@code toolCharacter} 缓存(5min TTL)消除重复 DB 查询。
 * key 包含 novelId,避免 R5 跨小说串数据。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CharacterQueryTool implements AiTool {

    private final NovelCharacterMapper characterDao;

    @Tool(name = "queryCharacter", value = {
            "根据人物姓名查询其档案(年龄/性格/外貌/武器/背景)。当需要描写某个角色的言行、确保人设不崩塌时调用此工具。"})
    @Cacheable(cacheManager = ToolCacheConfig.TOOL_CACHE_MANAGER,
            value = "toolCharacter",
            key = "(T(com.ink.speaker.common.NovelContext).getNovelId() ?: 0) + ':' + (#name ?: '')",
            unless = "#result == null || #result.startsWith('人物档案库中未找到')")
    public String queryCharacter(
            @P("人物姓名,例如 林晚、苏砚、赵九") String name) {
        Long novelId = NovelContext.requireNovelId();
        log.info("[Tool] queryCharacter novelId={} name={}", novelId, name);

        Optional<NovelCharacter> exact = characterDao.findByNovelIdAndName(novelId, name);
        if (exact.isPresent()) {
            return format(exact.get());
        }

        List<NovelCharacter> fuzzy = characterDao.searchByNovelIdAndNameContaining(novelId, name);
        if (!fuzzy.isEmpty()) {
            return format(fuzzy.get(0)) + "\n(模糊匹配结果,如非目标人物请用更精确的姓名重试)";
        }

        return String.format("人物档案库中未找到 '%s',可自行创作但要保持前后一致", name);
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

