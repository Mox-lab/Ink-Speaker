package com.ink.speaker.novel.service.impl;

import com.ink.speaker.common.BusinessException;
import com.ink.speaker.common.NovelContext;
import com.ink.speaker.common.ResultCode;
import com.ink.speaker.novel.domain.dto.NovelCreateRequest;
import com.ink.speaker.novel.domain.dto.NovelUpdateRequest;
import com.ink.speaker.novel.domain.entity.Novel;
import com.ink.speaker.novel.domain.entity.NovelChapterContent;
import com.ink.speaker.novel.domain.entity.NovelCharacter;
import com.ink.speaker.novel.domain.entity.NovelOutline;
import com.ink.speaker.novel.domain.entity.NovelWorldSetting;
import com.ink.speaker.novel.mapper.NovelChapterContentMapper;
import com.ink.speaker.novel.mapper.NovelChapterTimelineMapper;
import com.ink.speaker.novel.mapper.NovelCharacterMapper;
import com.ink.speaker.novel.mapper.NovelMapper;
import com.ink.speaker.novel.mapper.NovelOutlineMapper;
import com.ink.speaker.novel.mapper.NovelReviewIssueMapper;
import com.ink.speaker.novel.mapper.NovelWorldSettingMapper;
import com.ink.speaker.novel.service.NovelService;
import com.ink.speaker.novel.domain.vo.ChapterSummaryVo;
import com.ink.speaker.novel.domain.vo.NovelExportPayload;
import com.ink.speaker.novel.domain.vo.NovelOverviewVo;
import com.ink.speaker.novel.domain.vo.NovelVo;
import com.ink.speaker.novel.domain.vo.OutlineSummaryVo;
import com.ink.speaker.novel.domain.vo.SaveResultVo;
import com.ink.speaker.util.ArgsUtil;
import com.ink.speaker.util.JsonUtil;
import com.ink.speaker.util.VoConverters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 小说主表服务实现。
 *
 * <p>第 5 阶段(R5 用户隔离):</p>
 * <ul>
 *   <li>{@link #listNovels()} 仅返回当前用户拥有的小说(走 {@link NovelMapper#listByOwnerId})</li>
 *   <li>{@link #listSharedForReference()} 返回公共参考池的脱敏小说列表(跨小说参考)</li>
 * </ul>
 *
 * <p>第 6 阶段(以小说为主体):新增 CRUD 与概览接口。</p>
 * <ul>
 *   <li>{@link #createNovel} — 创建小说,ownerId 强制取当前用户</li>
 *   <li>{@link #updateNovel} — 更新基础信息,校验所有权</li>
 *   <li>{@link #deleteNovel} — 级联删除全部子表数据(单事务)</li>
 *   <li>{@link #getNovel} — 取单本(校验所有权)</li>
 *   <li>{@link #getNovelOverview} — 取概览(基础信息 + 各子模块统计 + 最近章节/大纲列表)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NovelServiceImpl implements NovelService {

    /** 概览页展示的最近章节数量。 */
    private static final int OVERVIEW_RECENT_CHAPTER_LIMIT = 5;

    /** 审查问题"未解决"状态值。 */
    private static final String ISSUE_STATUS_OPEN = "open";

    /** 支持的导出格式(小写)。 */
    private static final Set<String> SUPPORTED_EXPORT_FORMATS = Set.of("md", "txt", "json");

    /** 默认导出格式。 */
    private static final String DEFAULT_EXPORT_FORMAT = "md";

    /** 导出文件名安全替换字符(移除路径分隔符与控制字符,防止目录穿越)。 */
    private static final String FILENAME_SAFE_REPLACE = "[\\\\/:*?\"<>|\\p{Cntrl}]";

    /** ISO 风格时间格式器(用于导出元信息)。 */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final NovelMapper novelDao;
    private final NovelChapterContentMapper chapterDao;
    private final NovelOutlineMapper outlineDao;
    private final NovelCharacterMapper characterDao;
    private final NovelWorldSettingMapper settingDao;
    private final NovelChapterTimelineMapper timelineDao;
    private final NovelReviewIssueMapper reviewIssueDao;

    @Override
    public List<NovelVo> listNovels() {
        Long userId = NovelContext.requireUserId();
        List<NovelVo> novels = novelDao.listByOwnerId(userId).stream()
                .map(VoConverters::toVo)
                .toList();
        log.info("[listNovels] userId={}, size={}", userId, novels.size());
        return novels;
    }

    @Override
    public List<NovelVo> listSharedForReference() {
        List<Novel> novels = novelDao.listSharedForReference();
        List<NovelVo> result = novels.stream()
                .map(n -> NovelVo.builder()
                        .id(n.getId())
                        .title(n.getTitle())
                        .author(n.getAuthor())
                        .description(n.getDescription())
                        .build())
                .toList();
        log.info("[listSharedForReference] size={}", result.size());
        return result;
    }

    @Override
    public SaveResultVo createNovel(NovelCreateRequest request) {
        Long userId = NovelContext.requireUserId();
        Novel entity = Novel.builder()
                .title(request.title())
                .author(request.author())
                .description(request.description())
                .ownerId(userId)
                .sharedForReference(request.sharedForReference())
                .build();
        novelDao.insert(entity);
        log.info("[createNovel] userId={}, novelId={}, title={}",
                userId, entity.getId(), entity.getTitle());
        return new SaveResultVo(entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateNovel(Long id, NovelUpdateRequest request) {
        Long userId = NovelContext.requireUserId();
        Novel entity = requireOwnedNovel(id, userId);
        entity.setTitle(request.title());
        entity.setAuthor(request.author());
        entity.setDescription(request.description());
        entity.setSharedForReference(request.sharedForReference());
        novelDao.updateById(entity);
        log.info("[updateNovel] userId={}, novelId={}", userId, id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNovel(Long id) {
        Long userId = NovelContext.requireUserId();
        // 先校验所有权(不存在或不属于该用户时抛 404,避免暴露存在性)
        requireOwnedNovel(id, userId);

        // 级联删除子表(顺序无关,均在同一事务内)
        chapterDao.deleteByNovelId(id);
        outlineDao.deleteByNovelId(id);
        characterDao.deleteByNovelId(id);
        settingDao.deleteByNovelId(id);
        timelineDao.deleteByNovelId(id);
        reviewIssueDao.deleteByNovelId(id);

        // 最后删主表(带 ownerId 双重过滤兜底)
        int affected = novelDao.deleteByIdAndOwner(id, userId);
        if (affected == 0) {
            // 极端并发场景:校验通过后被其他请求删掉
            throw new BusinessException(ResultCode.NOT_FOUND, "小说不存在或已被删除: " + id);
        }
        log.info("[deleteNovel] userId={}, novelId={}, affected={}", userId, id, affected);
    }

    @Override
    public NovelVo getNovel(Long id) {
        Long userId = NovelContext.requireUserId();
        Novel entity = requireOwnedNovel(id, userId);
        return VoConverters.toVo(entity);
    }

    @Override
    public NovelOverviewVo getNovelOverview(Long id) {
        Long userId = NovelContext.requireUserId();
        Novel entity = requireOwnedNovel(id, userId);

        List<NovelChapterContent> recentChapters = chapterDao
                .listByNovelIdOrderByChapterNoAsc(id);
        List<ChapterSummaryVo> recentChapterVos = recentChapters.stream()
                .sorted((a, b) -> Integer.compare(b.getChapterNo(), a.getChapterNo()))
                .limit(OVERVIEW_RECENT_CHAPTER_LIMIT)
                .sorted((a, b) -> Integer.compare(a.getChapterNo(), b.getChapterNo()))
                .map(c -> VoConverters.toSummaryVo(c, ArgsUtil.truncate(c.getContent(), 150)))
                .toList();

        Integer latestChapterNo = recentChapters.stream()
                .map(NovelChapterContent::getChapterNo)
                .max(Integer::compare)
                .orElse(null);

        List<NovelOutline> outlines = outlineDao.listByNovelIdOrderByVersionDesc(id);
        List<OutlineSummaryVo> outlineVos = outlines.stream()
                .map(o -> VoConverters.toSummaryVo(o, ArgsUtil.truncate(o.getContent(), 150)))
                .toList();
        boolean hasActiveOutline = outlines.stream().anyMatch(NovelOutline::isActive);

        return NovelOverviewVo.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .author(entity.getAuthor())
                .description(entity.getDescription())
                .sharedForReference(entity.isSharedForReference())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .chapterCount(novelDao.countChapters(id))
                .latestChapterNo(latestChapterNo)
                .outlineCount(novelDao.countOutlines(id))
                .hasActiveOutline(hasActiveOutline)
                .characterCount(novelDao.countCharacters(id))
                .settingCount(novelDao.countSettings(id))
                .unresolvedIssueCount(novelDao.countReviewIssuesByStatus(id, ISSUE_STATUS_OPEN))
                .recentChapters(recentChapterVos)
                .outlines(outlineVos)
                .build();
    }

    /**
     * 取一本属于当前用户的小说,不存在或不属于该用户时抛 NOT_FOUND。
     * <p>统一返回 404 而非 403,避免暴露小说存在性(防止枚举攻击)。</p>
     */
    private Novel requireOwnedNovel(Long id, Long userId) {
        Novel entity = novelDao.findByIdAndOwner(id, userId);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "小说不存在或无权访问: " + id);
        }
        return entity;
    }

    @Override
    public NovelExportPayload exportNovel(Long id, String format) {
        Long userId = NovelContext.requireUserId();
        Novel entity = requireOwnedNovel(id, userId);

        String fmt = normalizeFormat(format);
        List<NovelChapterContent> chapters = chapterDao.listByNovelIdOrderByChapterNoAsc(id);
        List<NovelOutline> outlines = outlineDao.listByNovelIdOrderByVersionDesc(id);
        List<NovelCharacter> characters = characterDao.listByNovelId(id);
        List<NovelWorldSetting> settings = settingDao.listByNovelId(id);

        String filename = buildFilename(entity, fmt);
        String contentType = contentTypeFor(fmt);
        byte[] bytes = switch (fmt) {
            case "json" -> toJsonBytes(entity, outlines, characters, settings, chapters);
            case "txt" -> toTxtBytes(entity, outlines, characters, settings, chapters);
            default -> toMarkdownBytes(entity, outlines, characters, settings, chapters);
        };
        log.info("[exportNovel] userId={}, novelId={}, format={}, bytes={}",
                userId, id, fmt, bytes.length);
        return new NovelExportPayload(filename, contentType, bytes);
    }

    /**
     * 校验并归一化导出格式参数。
     * <p>大小写不敏感;不在白名单内抛 PARAM_INVALID。</p>
     */
    private String normalizeFormat(String format) {
        String fmt = format == null || format.isBlank()
                ? DEFAULT_EXPORT_FORMAT
                : format.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_EXPORT_FORMATS.contains(fmt)) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "不支持的导出格式: " + format + "(仅支持 md / txt / json)");
        }
        return fmt;
    }

    /**
     * 构造下载文件名,对标题做安全清洗避免路径穿越。
     * <p>格式:novel-{id}-{sanitized-title}.{ext}</p>
     */
    private String buildFilename(Novel entity, String fmt) {
        String rawTitle = entity.getTitle() == null ? "" : entity.getTitle().trim();
        String safeTitle = rawTitle.replaceAll(FILENAME_SAFE_REPLACE, "_");
        if (safeTitle.isBlank()) {
            safeTitle = "untitled";
        }
        // 控制文件名长度,避免某些文件系统限制(255 字符)
        if (safeTitle.length() > 60) {
            safeTitle = safeTitle.substring(0, 60);
        }
        return "novel-" + entity.getId() + "-" + safeTitle + "." + fmt;
    }

    /**
     * 各格式对应的 Content-Type(已含 charset=UTF-8)。
     */
    private String contentTypeFor(String fmt) {
        return switch (fmt) {
            case "json" -> "application/json; charset=UTF-8";
            case "txt" -> "text/plain; charset=UTF-8";
            default -> "text/markdown; charset=UTF-8";
        };
    }

    /**
     * Markdown 拼装:小说信息 + 大纲 + 人物表 + 设定表 + 章节正文。
     */
    private byte[] toMarkdownBytes(Novel entity,
                                   List<NovelOutline> outlines,
                                   List<NovelCharacter> characters,
                                   List<NovelWorldSetting> settings,
                                   List<NovelChapterContent> chapters) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(safe(entity.getTitle())).append("\n\n");
        sb.append("- 作者: ").append(safe(entity.getAuthor())).append("\n");
        if (entity.getDescription() != null && !entity.getDescription().isBlank()) {
            sb.append("- 简介: ").append(safe(entity.getDescription())).append("\n");
        }
        sb.append("- 创建时间: ").append(formatTime(entity.getCreatedAt())).append("\n");
        sb.append("- 更新时间: ").append(formatTime(entity.getUpdatedAt())).append("\n\n");

        // 大纲
        sb.append("## 大纲\n\n");
        if (outlines.isEmpty()) {
            sb.append("(暂无大纲)\n\n");
        } else {
            for (NovelOutline o : outlines) {
                sb.append("### ").append(safe(o.getTitle())).append("\n\n");
                if (o.getTheme() != null && !o.getTheme().isBlank()) {
                    sb.append("**主题**: ").append(safe(o.getTheme())).append("\n\n");
                }
                sb.append("- 版本: v").append(o.getVersion()).append("\n");
                sb.append("- 激活: ").append(o.isActive() ? "是" : "否").append("\n");
                if (o.getChapters() != null) {
                    sb.append("- 计划章数: ").append(o.getChapters()).append("\n");
                }
                sb.append("\n```\n").append(safe(o.getContent())).append("\n```\n\n");
            }
        }

        // 人物
        sb.append("## 人物\n\n");
        if (characters.isEmpty()) {
            sb.append("(暂无人物)\n\n");
        } else {
            sb.append("| 姓名 | 年龄 | 性别 | 武器 | 性格 | 背景 |\n");
            sb.append("| --- | --- | --- | --- | --- | --- |\n");
            for (NovelCharacter c : characters) {
                sb.append("| ").append(safe(c.getName()))
                        .append(" | ").append(c.getAge() == null ? "" : c.getAge())
                        .append(" | ").append(safe(c.getGender()))
                        .append(" | ").append(safe(c.getWeapon()))
                        .append(" | ").append(safe(ArgsUtil.truncate(c.getPersonality(), 60)))
                        .append(" | ").append(safe(ArgsUtil.truncate(c.getBackground(), 60)))
                        .append(" |\n");
            }
            sb.append("\n");
            for (NovelCharacter c : characters) {
                sb.append("### ").append(safe(c.getName())).append("\n\n");
                if (c.getPersonality() != null && !c.getPersonality().isBlank()) {
                    sb.append("- 性格: ").append(safe(c.getPersonality())).append("\n");
                }
                if (c.getBackground() != null && !c.getBackground().isBlank()) {
                    sb.append("- 背景: ").append(safe(c.getBackground())).append("\n");
                }
                if (c.getIdentity() != null && !c.getIdentity().isBlank()) {
                    sb.append("- 身份: ").append(safe(c.getIdentity())).append("\n");
                }
                if (c.getAppearance() != null && !c.getAppearance().isBlank()) {
                    sb.append("- 外貌: ").append(safe(c.getAppearance())).append("\n");
                }
                sb.append("\n");
            }
        }

        // 世界观
        sb.append("## 世界观设定\n\n");
        if (settings.isEmpty()) {
            sb.append("(暂无设定)\n\n");
        } else {
            sb.append("| 关键词 | 分类 | 描述 |\n");
            sb.append("| --- | --- | --- |\n");
            for (NovelWorldSetting s : settings) {
                sb.append("| ").append(safe(s.getKeyword()))
                        .append(" | ").append(safe(s.getCategory()))
                        .append(" | ").append(safe(ArgsUtil.truncate(s.getDescription(), 80)))
                        .append(" |\n");
            }
            sb.append("\n");
            for (NovelWorldSetting s : settings) {
                sb.append("### ").append(safe(s.getKeyword())).append("\n\n");
                if (s.getCategory() != null && !s.getCategory().isBlank()) {
                    sb.append("**分类**: ").append(safe(s.getCategory())).append("\n\n");
                }
                sb.append(safe(s.getDescription())).append("\n\n");
            }
        }

        // 章节
        sb.append("## 章节\n\n");
        if (chapters.isEmpty()) {
            sb.append("(暂无章节)\n\n");
        } else {
            for (NovelChapterContent c : chapters) {
                sb.append("### 第 ").append(c.getChapterNo()).append(" 章 · ")
                        .append(safe(c.getTitle())).append("\n\n");
                sb.append(safe(c.getContent())).append("\n\n---\n\n");
            }
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 纯文本拼装:小说信息头 + 章节正文(按章节序)。
     */
    private byte[] toTxtBytes(Novel entity,
                              List<NovelOutline> outlines,
                              List<NovelCharacter> characters,
                              List<NovelWorldSetting> settings,
                              List<NovelChapterContent> chapters) {
        StringBuilder sb = new StringBuilder();
        sb.append(safe(entity.getTitle())).append("\n");
        sb.append("作者: ").append(safe(entity.getAuthor())).append("\n");
        if (entity.getDescription() != null && !entity.getDescription().isBlank()) {
            sb.append("简介: ").append(safe(entity.getDescription())).append("\n");
        }
        sb.append("\n");

        if (chapters.isEmpty()) {
            sb.append("(暂无章节)\n");
        } else {
            for (NovelChapterContent c : chapters) {
                sb.append("第 ").append(c.getChapterNo()).append(" 章 ")
                        .append(safe(c.getTitle())).append("\n");
                sb.append("----------------------------------------\n");
                sb.append(safe(c.getContent())).append("\n\n");
            }
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * JSON 完整结构化数据,使用 LinkedHashMap 保持字段顺序。
     */
    private byte[] toJsonBytes(Novel entity,
                               List<NovelOutline> outlines,
                               List<NovelCharacter> characters,
                               List<NovelWorldSetting> settings,
                               List<NovelChapterContent> chapters) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> novelInfo = new LinkedHashMap<>();
        novelInfo.put("id", entity.getId());
        novelInfo.put("title", entity.getTitle());
        novelInfo.put("author", entity.getAuthor());
        novelInfo.put("description", entity.getDescription());
        novelInfo.put("sharedForReference", entity.isSharedForReference());
        novelInfo.put("createdAt", formatTime(entity.getCreatedAt()));
        novelInfo.put("updatedAt", formatTime(entity.getUpdatedAt()));
        root.put("novel", novelInfo);

        root.put("outlines", outlines.stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId());
            m.put("title", o.getTitle());
            m.put("theme", o.getTheme());
            m.put("chapters", o.getChapters());
            m.put("version", o.getVersion());
            m.put("active", o.isActive());
            m.put("content", o.getContent());
            return m;
        }).toList());

        root.put("characters", characters.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("age", c.getAge());
            m.put("gender", c.getGender());
            m.put("personality", c.getPersonality());
            m.put("weapon", c.getWeapon());
            m.put("background", c.getBackground());
            m.put("identity", c.getIdentity());
            m.put("appearance", c.getAppearance());
            m.put("relationships", JsonUtil.parseMap(c.getRelationships()));
            return m;
        }).toList());

        root.put("worldSettings", settings.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("keyword", s.getKeyword());
            m.put("category", s.getCategory());
            m.put("description", s.getDescription());
            return m;
        }).toList());

        root.put("chapters", chapters.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("chapterNo", c.getChapterNo());
            m.put("title", c.getTitle());
            m.put("wordCount", c.getWordCount());
            m.put("content", c.getContent());
            return m;
        }).toList());

        try {
            return JsonUtil.MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(root);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR,
                    "导出 JSON 序列化失败: " + ArgsUtil.reasonOf(e));
        }
    }

    /**
     * 安全输出字符串,null 时返回空串。
     */
    private String safe(String s) {
        return s == null ? "" : s;
    }

    /**
     * 格式化时间(导出元信息使用)。
     */
    private String formatTime(java.time.LocalDateTime ts) {
        return ts == null ? "" : EXPORT_TIME_FORMATTER.format(ts);
    }
}
