package ink.realm.novel.service.impl;

import ink.realm.common.exception.BusinessException;
import ink.realm.common.context.NovelContext;
import ink.realm.common.result.ResultCode;
import ink.realm.novel.domain.dto.NovelCreateRequest;
import ink.realm.novel.domain.dto.NovelUpdateRequest;
import ink.realm.auth.domain.entity.User;
import ink.realm.novel.domain.entity.Novel;
import ink.realm.novel.domain.entity.NovelChapterContent;
import ink.realm.novel.domain.entity.NovelCollaborator;
import ink.realm.novel.domain.entity.NovelOutline;
import ink.realm.novel.domain.entity.NovelWorldSetting;
import ink.realm.novel.mapper.NovelChapterContentMapper;
import ink.realm.novel.mapper.NovelChapterTimelineMapper;
import ink.realm.novel.mapper.NovelMapper;
import ink.realm.novel.mapper.NovelOutlineMapper;
import ink.realm.novel.mapper.NovelReviewIssueMapper;
import ink.realm.novel.mapper.NovelCollaboratorMapper;
import ink.realm.novel.mapper.NovelWorldSettingMapper;
import ink.realm.novel.service.CollaboratorService;
import ink.realm.novel.service.NovelService;
import ink.realm.auth.mapper.UserMapper;
import ink.realm.config.security.SecurityRoles;
import ink.realm.novel.domain.vo.ChapterSummaryVo;
import ink.realm.novel.domain.vo.CharacterVo;
import ink.realm.novel.domain.vo.NovelExportPayload;
import ink.realm.novel.domain.vo.NovelOverviewVo;
import ink.realm.novel.domain.vo.NovelVo;
import ink.realm.novel.domain.vo.OutlineSummaryVo;
import ink.realm.novel.domain.vo.SaveResultVo;
import ink.realm.novel.domain.vo.SharedNovelBrowseVo;
import ink.realm.novel.domain.vo.TimelineEventVo;
import ink.realm.novel.domain.entity.NovelReviewIssue;
import ink.realm.util.ArgsUtil;
import ink.realm.util.JsonUtil;
import ink.realm.util.VoConverters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

    /** BASE-08 时间线聚合最大条数。 */
    private static final int OVERVIEW_TIMELINE_LIMIT = 20;

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
    private final NovelWorldSettingMapper settingDao;
    private final NovelChapterTimelineMapper timelineDao;
    private final NovelReviewIssueMapper reviewIssueDao;
    private final UserMapper userMapper;
    private final NovelCollaboratorMapper collaboratorMapper;
    private final CollaboratorService collaboratorService;

    @Override
    public List<NovelVo> listNovels() {
        Long userId = NovelContext.requireUserId();

        // 1. 自己 owner 的小说
        List<NovelVo> owned = novelDao.listByOwnerId(userId).stream()
                .map(n -> {
                    NovelVo v = VoConverters.toVo(n);
                    v.setCollaborator(false);
                    v.setCollaboratorRole(null);
                    return v;
                })
                .toList();

        // 2. 协作的小说(BASE-11):通过 novel_collaborator 反查
        List<Long> collabNovelIds = collaboratorMapper.listNovelIdsByUserId(userId);
        List<NovelVo> collab = collabNovelIds.stream()
                .map(novelDao::selectById)
                .filter(Objects::nonNull)
                .map(n -> {
                    NovelVo v = VoConverters.toVo(n);
                    v.setCollaborator(true);
                    // 角色:owner 不会出现在协作表里,这里只会是 editor / viewer
                    NovelCollaborator c = collaboratorMapper.findByNovelIdAndUserId(n.getId(), userId);
                    v.setCollaboratorRole(c != null ? c.getRole() : null);
                    return v;
                })
                .toList();

        List<NovelVo> result = new ArrayList<>(owned);
        result.addAll(collab);
        log.info("[listNovels] userId={}, owned={}, collab={}", userId, owned.size(), collab.size());
        return result;
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
        // 用户维度小说名唯一:同一用户下不允许有重名小说
        if (novelDao.findByOwnerAndTitle(userId, request.title()).isPresent()) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "你已创建同名小说《" + request.title() + "》,请勿重复创建");
        }
        // 作者名强制取当前登录用户的昵称(昵称为空时回退用户名),不再信任前端传入值
        String author = resolveCurrentAuthorName();
        Novel entity = Novel.builder()
                .title(request.title())
                .author(author)
                .description(request.description())
                .ownerId(userId)
                .sharedForReference(request.sharedForReference())
                .build();
        novelDao.insert(entity);
        log.info("[createNovel] userId={}, novelId={}, title={}, author={}",
                userId, entity.getId(), entity.getTitle(), author);
        return new SaveResultVo(entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateNovel(Long id, NovelUpdateRequest request) {
        Long userId = NovelContext.requireUserId();
        // 管理员不可修改他人小说(只读约束),非 owner 一律 403
        Novel entity = requireModifiableNovel(id, userId);
        // 用户维度小说名唯一:若改名,不能与同用户下其它小说重名
        if (!entity.getTitle().equals(request.title())
                && novelDao.findByOwnerAndTitleExcluding(userId, request.title(), id).isPresent()) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "你已创建同名小说《" + request.title() + "》,请勿重复命名");
        }
        entity.setTitle(request.title());
        // 作者名始终取当前登录用户的昵称,忽略前端传入值
        entity.setAuthor(resolveCurrentAuthorName());
        entity.setDescription(request.description());
        entity.setSharedForReference(request.sharedForReference());
        novelDao.updateById(entity);
        log.info("[updateNovel] userId={}, novelId={}", userId, id);
    }

    /**
     * 取当前登录用户的展示名(用于 author 字段)。
     * <p>优先使用昵称;若用户尚未设置昵称(为空),回退为用户名,
     * 保证作者字段始终有值。</p>
     */
    private String resolveCurrentAuthorName() {
        Long userId = NovelContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录,无法解析作者");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在,无法解析作者");
        }
        String name = user.getNickname();
        if (name == null || name.isBlank()) {
            name = user.getUsername();
        }
        return name;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNovel(Long id) {
        Long userId = NovelContext.requireUserId();
        // 先校验可修改性(不存在或不属于该用户时抛 404;管理员改他人小说抛 403,避免暴露存在性)
        requireModifiableNovel(id, userId);

        // 级联删除子表(顺序无关,均在同一事务内)
        chapterDao.deleteByNovelId(id);
        outlineDao.deleteByNovelId(id);
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
        // owner / editor / viewer / admin 均可读取(BASE-11 多用户协作)
        collaboratorService.requireViewerAccess(id, userId);
        return VoConverters.toVo(novelDao.selectById(id));
    }

    @Override
    public NovelOverviewVo getNovelOverview(Long id) {
        Long userId = NovelContext.requireUserId();
        // owner / editor / viewer / admin 均可读取(BASE-11 多用户协作)
        collaboratorService.requireViewerAccess(id, userId);
        Novel entity = novelDao.selectById(id);

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

        List<TimelineEventVo> timeline = buildTimeline(
                recentChapters, outlines,
                settingDao.listByNovelIdAndCategory(id, "人物"),
                settingDao.listByNovelId(id),
                reviewIssueDao.listByNovelIdAndStatusOrderByChapterNoAsc(id, ISSUE_STATUS_OPEN));

        return NovelOverviewVo.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .author(entity.getAuthor())
                .description(entity.getDescription())
                .sharedForReference(entity.isSharedForReference())
                .createdAt(entity.getCtTime())
                .updatedAt(entity.getUtTime())
                .chapterCount(novelDao.countChapters(id))
                .latestChapterNo(latestChapterNo)
                .outlineCount(novelDao.countOutlines(id))
                .hasActiveOutline(hasActiveOutline)
                .characterCount(novelDao.countCharacters(id))
                .settingCount(novelDao.countSettings(id))
                .unresolvedIssueCount(novelDao.countReviewIssuesByStatus(id, ISSUE_STATUS_OPEN))
                .recentChapters(recentChapterVos)
                .outlines(outlineVos)
                .timeline(timeline)
                .role(collaboratorService.resolveRole(id, userId))
                .build();
    }

    /**
     * BASE-08 工作台时间线聚合。
     * <p>按时间倒序合并最近章节保存 / 大纲创建 / 人物更新 / 设定更新 / 审查问题反馈等事件,
     * 截取最近 {@link #OVERVIEW_TIMELINE_LIMIT} 条供前端时间线组件渲染。</p>
     */
    private List<TimelineEventVo> buildTimeline(List<NovelChapterContent> chapters,
                                                List<NovelOutline> outlines,
                                                List<NovelWorldSetting> characters,
                                                List<NovelWorldSetting> settings,
                                                List<NovelReviewIssue> issues) {
        List<TimelineEventVo> events = new ArrayList<>(chapters.size()
                + outlines.size() + characters.size() + settings.size() + issues.size());

        for (NovelChapterContent c : chapters) {
            if (c.getUtTime() == null) {
                continue;
            }
            events.add(TimelineEventVo.builder()
                    .type("chapter_saved")
                    .resourceId(c.getId())
                    .title("第 " + c.getChapterNo() + " 章 · " + safe(c.getTitle()))
                    .description(ArgsUtil.truncate(c.getContent(), 80))
                    .timestamp(c.getUtTime())
                    .build());
        }
        for (NovelOutline o : outlines) {
            if (o.getCtTime() == null) {
                continue;
            }
            events.add(TimelineEventVo.builder()
                    .type("outline_created")
                    .resourceId(o.getId())
                    .title("大纲 v" + o.getVersion() + " · " + safe(o.getTitle()))
                    .description(o.isActive() ? "激活版本" : "历史版本")
                    .timestamp(o.getCtTime())
                    .build());
        }
        for (NovelWorldSetting ch : characters) {
            if (ch.getUtTime() == null) {
                continue;
            }
            // 人物结构化字段内嵌于 description,经 toCharacterVo 解析取性格摘要
            CharacterVo cv = VoConverters.toCharacterVo(ch);
            events.add(TimelineEventVo.builder()
                    .type("character_added")
                    .resourceId(ch.getId())
                    .title("人物 · " + safe(ch.getKeyword()))
                    .description(ArgsUtil.truncate(cv.getPersonality(), 80))
                    .timestamp(ch.getUtTime())
                    .build());
        }
        for (NovelWorldSetting s : settings) {
            if (s.getUtTime() == null) {
                continue;
            }
            events.add(TimelineEventVo.builder()
                    .type("setting_added")
                    .resourceId(s.getId())
                    .title("设定 · " + safe(s.getKeyword()))
                    .description(ArgsUtil.truncate(s.getDescription(), 80))
                    .timestamp(s.getUtTime())
                    .build());
        }
        for (NovelReviewIssue i : issues) {
            if (i.getCtTime() == null) {
                continue;
            }
            events.add(TimelineEventVo.builder()
                    .type("review_added")
                    .resourceId(i.getId())
                    .title("审查问题 · 第 " + i.getChapterNo() + " 章")
                    .description(ArgsUtil.truncate(i.getDescription(), 80))
                    .timestamp(i.getCtTime())
                    .build());
        }

        events.sort(Comparator.comparing(TimelineEventVo::getTimestamp,
                Comparator.nullsLast(Comparator.reverseOrder())));
        if (events.size() > OVERVIEW_TIMELINE_LIMIT) {
            return new ArrayList<>(events.subList(0, OVERVIEW_TIMELINE_LIMIT));
        }
        return events;
    }

    /**
     * 取一本属于当前用户的小说,不存在或不属于该用户时抛 NOT_FOUND。
     * <p>统一返回 404 而非 403,避免暴露小说存在性(防止枚举攻击)。</p>
     */
    private Novel requireOwnedNovel(Long id, Long userId) {
        return novelDao.findByIdAndOwner(id, userId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "小说不存在或无权访问: " + id));
    }

    /**
     * 判断当前登录用户是否为管理员(ROLE_ADMIN)。
     * <p>从 Spring Security 的 {@code SecurityContext} 解析 authorities,
     * 与 {@link SecurityRoles#ADMIN} 比对。</p>
     */
    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> SecurityRoles.ADMIN.equals(a.getAuthority()));
    }

    /**
     * 取小说用于只读访问:owner 或管理员均可访问;其他用户返回 404(避免暴露存在性)。
     */
    private Novel requireOwnedOrAdminReadOnly(Long id, Long userId) {
        if (isAdmin()) {
            Novel entity = novelDao.selectById(id);
            if (entity == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "小说不存在: " + id);
            }
            return entity;
        }
        return requireOwnedNovel(id, userId);
    }

    /**
     * 取小说用于修改:owner 可改;管理员非 owner 一律 403(只读约束,不可修改/删除他人小说);
     * 普通用户非 owner 返回 404(避免暴露存在性)。
     */
    private Novel requireModifiableNovel(Long id, Long userId) {
        Novel entity = novelDao.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "小说不存在或无权访问: " + id);
        }
        if (entity.getOwnerId().equals(userId)) {
            return entity;
        }
        if (isAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "管理员仅可只读查看他人小说,不可修改");
        }
        throw new BusinessException(ResultCode.NOT_FOUND, "小说不存在或无权访问: " + id);
    }

    @Override
    public SharedNovelBrowseVo getSharedNovelBrowse(Long id) {
        // 不校验所有权 —— 公共池跨用户可见,但必须 sharedForReference=true
        Novel entity = novelDao.findByIdAndShared(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "小说不存在或未公开: " + id));

        List<NovelChapterContent> chapters = chapterDao.listByNovelIdOrderByChapterNoAsc(id);
        List<NovelOutline> outlines = outlineDao.listByNovelIdOrderByVersionDesc(id);
        // 人物统一来源于设定集「人物」分类
        List<NovelWorldSetting> characters = settingDao.listByNovelIdAndCategory(id, "人物");
        List<NovelWorldSetting> settings = settingDao.listByNovelId(id);

        log.info("[getSharedNovelBrowse] novelId={}, chapters={}, outlines={}, characters={}, settings={}",
                id, chapters.size(), outlines.size(), characters.size(), settings.size());

        return SharedNovelBrowseVo.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .author(entity.getAuthor())
                .description(entity.getDescription())
                .createdAt(entity.getCtTime())
                .updatedAt(entity.getUtTime())
                .chapterCount(chapters.size())
                .outlineCount(outlines.size())
                .characterCount(characters.size())
                .settingCount(settings.size())
                .chapters(chapters.stream()
                        .map(c -> VoConverters.toSummaryVo(c, ArgsUtil.truncate(c.getContent(), 150)))
                        .toList())
                .outlines(outlines.stream()
                        .map(o -> VoConverters.toSummaryVo(o, ArgsUtil.truncate(o.getContent(), 200)))
                        .toList())
                .characters(characters.stream()
                        .map(VoConverters::toCharacterVo)
                        .toList())
                .settings(settings.stream()
                        .map(VoConverters::toVo)
                        .toList())
                .build();
    }

    @Override
    public NovelExportPayload exportNovel(Long id, String format) {
        Long userId = NovelContext.requireUserId();
        // owner / editor / viewer / admin 均可导出(BASE-11 多用户协作)
        collaboratorService.requireViewerAccess(id, userId);
        Novel entity = novelDao.selectById(id);

        String fmt = normalizeFormat(format);
        List<NovelChapterContent> chapters = chapterDao.listByNovelIdOrderByChapterNoAsc(id);
        List<NovelOutline> outlines = outlineDao.listByNovelIdOrderByVersionDesc(id);
        // 人物统一来源于设定集「人物」分类,转换为 CharacterVo 供导出渲染
        List<NovelWorldSetting> charSettings = settingDao.listByNovelIdAndCategory(id, "人物");
        List<CharacterVo> characters = charSettings.stream()
                .map(VoConverters::toCharacterVo)
                .toList();
        List<NovelWorldSetting> settings = settingDao.listByNovelId(id);

        // 文件名仅含"小说名_昵称"(昵称取小说所有者展示名,无昵称回退用户名)
        String nickname = resolveDisplayName(entity.getOwnerId());
        String filename = buildFilename(entity, fmt, nickname);
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
     * 取指定用户的展示名(昵称优先,无昵称回退用户名,均无则回退 "author")。
     * <p>不抛异常,保证文件名总能落到一个可用值。</p>
     */
    private String resolveDisplayName(Long userId) {
        if (userId == null) return "author";
        User user = userMapper.selectById(userId);
        if (user == null) return "author";
        String name = user.getNickname();
        if (name == null || name.isBlank()) {
            name = user.getUsername();
        }
        return (name == null || name.isBlank()) ? "author" : name;
    }

    /**
     * 构造下载文件名,对标题与昵称做安全清洗避免路径穿越。
     * <p>格式:{小说名}_{昵称}.{ext}</p>
     */
    private String buildFilename(Novel entity, String fmt, String nickname) {
        String rawTitle = entity.getTitle() == null ? "" : entity.getTitle().trim();
        String safeTitle = rawTitle.replaceAll(FILENAME_SAFE_REPLACE, "_");
        if (safeTitle.isBlank()) {
            safeTitle = "untitled";
        }
        // 控制文件名长度,避免某些文件系统限制(255 字符)
        if (safeTitle.length() > 60) {
            safeTitle = safeTitle.substring(0, 60);
        }
        String safeNick = (nickname == null ? "" : nickname)
                .replaceAll(FILENAME_SAFE_REPLACE, "_")
                .trim();
        if (safeNick.isBlank()) {
            safeNick = "author";
        }
        if (safeNick.length() > 30) {
            safeNick = safeNick.substring(0, 30);
        }
        return safeTitle + "_" + safeNick + "." + fmt;
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
                                   List<CharacterVo> characters,
                                   List<NovelWorldSetting> settings,
                                   List<NovelChapterContent> chapters) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(safe(entity.getTitle())).append("\n\n");
        sb.append("- 作者: ").append(safe(entity.getAuthor())).append("\n");
        if (entity.getDescription() != null && !entity.getDescription().isBlank()) {
            sb.append("- 简介: ").append(safe(entity.getDescription())).append("\n");
        }
        sb.append("- 创建时间: ").append(formatTime(entity.getCtTime())).append("\n");
        sb.append("- 更新时间: ").append(formatTime(entity.getUtTime())).append("\n\n");

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
            for (CharacterVo c : characters) {
                sb.append("| ").append(safe(c.getName()))
                        .append(" | ").append(c.getAge() == null ? "" : c.getAge())
                        .append(" | ").append(safe(c.getGender()))
                        .append(" | ").append(safe(c.getWeapon()))
                        .append(" | ").append(safe(ArgsUtil.truncate(c.getPersonality(), 60)))
                        .append(" | ").append(safe(ArgsUtil.truncate(c.getBackground(), 60)))
                        .append(" |\n");
            }
            sb.append("\n");
            for (CharacterVo c : characters) {
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
                              List<CharacterVo> characters,
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
                               List<CharacterVo> characters,
                               List<NovelWorldSetting> settings,
                               List<NovelChapterContent> chapters) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> novelInfo = new LinkedHashMap<>();
        novelInfo.put("id", entity.getId());
        novelInfo.put("title", entity.getTitle());
        novelInfo.put("author", entity.getAuthor());
        novelInfo.put("description", entity.getDescription());
        novelInfo.put("sharedForReference", entity.isSharedForReference());
        novelInfo.put("createdAt", formatTime(entity.getCtTime()));
        novelInfo.put("updatedAt", formatTime(entity.getUtTime()));
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
            m.put("relationships", c.getRelationships());
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
