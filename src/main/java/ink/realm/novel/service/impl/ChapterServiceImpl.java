package ink.realm.novel.service.impl;

import ink.realm.common.exception.BusinessException;
import ink.realm.common.context.NovelContext;
import ink.realm.common.result.ResultCode;
import ink.realm.ai.core.director.DirectorAgent;
import ink.realm.novel.domain.entity.NovelChapterHistory;
import ink.realm.novel.mapper.NovelChapterContentMapper;
import ink.realm.novel.mapper.NovelChapterHistoryMapper;
import ink.realm.novel.domain.dto.ChapterSaveRequest;
import ink.realm.novel.domain.entity.NovelChapterContent;
import ink.realm.novel.service.ChapterService;
import ink.realm.novel.domain.vo.ChapterDetailVo;
import ink.realm.novel.domain.vo.ChapterSummaryVo;
import ink.realm.novel.domain.vo.SaveResultVo;
import ink.realm.ai.core.memory.LongTermMemoryExtractor;
import ink.realm.util.ArgsUtil;
import ink.realm.util.NovelConstants;
import ink.realm.util.VoConverters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;

import java.util.List;

/**
 * 章节服务实现。
 * <p>负责章节正文 CRUD;保存后异步触发的记忆抽取与审查由本类协调(保持原业务行为)。</p>
 *
 * <p><b>novelId 解析顺序:</b></p>
 * <ol>
 *   <li>请求体显式携带(ChapterSaveRequest.novelId)</li>
 *   <li>{@link NovelContext}(由 X-Novel-Id 头注入)</li>
 *   <li>配置兜底 {@code ink.current-id}(仅单小说场景)</li>
 * </ol>
 *
 * <p>异步调用记忆抽取与审查时显式传入 novelId,因 ThreadLocal 不跨线程。</p>
 *
 * <p>第 4 阶段:saveChapter 加 {@link Propagation#REQUIRES_NEW} 隔离异步触发的副作用,
 * 捕获 {@link MybatisPlusException} 给出友好提示。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterServiceImpl implements ChapterService {

    /** 章节保存冲突时最大重试次数(乐观锁失败后用版本号重读)。 */
    public static final int SAVE_RETRY_MAX = 3;

    /** 历史快照保留版本数(优化清单 #60):超出此值的旧快照自动删除。 */
    public static final int HISTORY_KEEP_VERSIONS = 10;

    private final NovelChapterContentMapper chapterDao;
    private final NovelChapterHistoryMapper historyDao;
    private final LongTermMemoryExtractor memoryExtractor;
    private final DirectorAgent directorAgent;

    @Override
    public List<ChapterSummaryVo> listChapters(Long novelId) {
        Long resolved = resolveNovelId(novelId);
        return chapterDao.listByNovelIdOrderByChapterNoAsc(resolved).stream()
                .map(c -> VoConverters.toSummaryVo(c, ArgsUtil.truncate(c.getContent(), 150)))
                .toList();
    }

    @Override
    public ChapterDetailVo getChapter(Long id) {
        NovelChapterContent c = chapterDao.selectById(id);
        if (c == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "章节不存在: " + id);
        }
        return VoConverters.toDetailVo(c);
    }

    /**
     * 保存章节(带乐观锁重试 + 多设备冲突检测)。
     *
     * <p>并发场景:作者本地编辑器 + 服务端后台异步审查同时改章节时,
     * JPA {@code @Version} 会在 UPDATE 时校验版本,失败抛
     * {@link MybatisPlusException}。这里捕获后重试最多
     * {@link #SAVE_RETRY_MAX} 次,仍失败则返回 409 给前端提示"内容已被他人修改"。</p>
     *
     * <p>UX-08 多设备冲突检测:若请求携带 {@code clientUpdatedAt}(客户端加载该章节时的时间戳),
     * 则与服务端当前 updatedAt 比对;服务端 updatedAt 更新则说明已被其他设备保存过,
     * 直接返回 {@link ResultCode#CONFLICT_4091},前端弹冲突合并对话框。</p>
     *
     * <p>事务边界:本方法走默认 REQUIRED 事务;异步触发的 memoryExtractor / directorAgent
     * 在事务外执行(它们是 @Async,提交到 novelAsyncExecutor 后立即返回)。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaveResultVo saveChapter(ChapterSaveRequest request) {
        Long novelId = resolveNovelId(request.novelId());
        Integer chapterNo = request.chapterNo();
        String content = request.content();

        // UX-08:多设备冲突检测 — 若客户端携带 clientUpdatedAt,与服务端 updatedAt 比对
        java.time.LocalDateTime clientUpdatedAt = request.clientUpdatedAt();
        if (clientUpdatedAt != null) {
            chapterDao.findByNovelIdAndChapterNo(novelId, chapterNo).ifPresent(existing -> {
                java.time.LocalDateTime serverUpdatedAt = existing.getUtTime();
                if (serverUpdatedAt != null && serverUpdatedAt.isAfter(clientUpdatedAt)) {
                    log.warn("[saveChapter] conflict detected: client={}, server={}",
                            clientUpdatedAt, serverUpdatedAt);
                    throw new BusinessException(ResultCode.CONFLICT_4091,
                            "章节已被其他设备修改,请刷新后合并");
                }
            });
        }

        for (int attempt = 1; attempt <= SAVE_RETRY_MAX; attempt++) {
            try {
                NovelChapterContent chapter = chapterDao.findByNovelIdAndChapterNo(novelId, chapterNo)
                        .orElseGet(() -> NovelChapterContent.builder()
                                .novelId(novelId)
                                .chapterNo(chapterNo)
                                .build());
                chapter.setTitle(request.title() != null ? request.title() : "");
                chapter.setContent(content);
                chapter.setWordCount(content.length());
                chapter.setSessionId(request.sessionId() != null ? request.sessionId() : "");
                if (request.outlineId() != null) {
                    chapter.setOutlineId(request.outlineId());
                }

                if (chapter.getId() == null) {
                    chapterDao.insert(chapter);
                } else {
                    // 乐观锁:拦截器已在 UPDATE 的 WHERE 追加 version = ?,
                    // 版本过期会导致 0 行受影响,主动抛出以触发重试逻辑
                    int updated = chapterDao.updateById(chapter);
                    if (updated == 0) {
                        throw new MybatisPlusException("乐观锁冲突:章节版本已被其他会话更新");
                    }
                }
                log.info("[saveChapter] novelId={}, chapterNo={}, id={}, attempt={}",
                        novelId, chapterNo, chapter.getId(), attempt);

                // BASE-07:保存章节后插入一条历史快照(便于回溯)
                Long snapshotVersion = chapter.getVersion() != null ? chapter.getVersion() : 0L;
                NovelChapterHistory snapshot = NovelChapterHistory.builder()
                        .novelId(novelId)
                        .chapterId(chapter.getId())
                        .chapterNo(chapterNo)
                        .title(chapter.getTitle())
                        .content(content)
                        .wordCount(chapter.getWordCount())
                        .sessionId(chapter.getSessionId())
                        .snapshotVersion(snapshotVersion)
                        .build();
                try {
                    historyDao.insert(snapshot);
                    // 优化清单 #60:历史快照仅保留最近 10 版,超出部分自动清理
                    pruneHistorySnapshots(chapter.getId());
                } catch (Exception ex) {
                    log.warn("[saveChapter] failed to insert history snapshot: {}", ex.getMessage());
                }

                // 异步触发长期记忆抽取 + 章节审查
                // 显式传入 novelId:ThreadLocal 不会跨 @Async 线程继承
                memoryExtractor.extractAndPersistCharacters(content, chapterNo, novelId);
                directorAgent.reviewChapter(content, chapterNo, novelId);

                return new SaveResultVo(chapter.getId());
            } catch (MybatisPlusException e) {
                log.warn("[saveChapter] optimistic lock conflict, attempt={}/{}: {}",
                        attempt, SAVE_RETRY_MAX, e.getMessage());
                if (attempt == SAVE_RETRY_MAX) {
                    throw new BusinessException(ResultCode.CONFLICT,
                            "章节已被其他会话修改,请刷新后重试");
                }
            }
        }
        // 理论不可达(上面循环要么 return 要么 throw)
        throw new BusinessException(ResultCode.BUSINESS_ERROR, "章节保存失败");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteChapter(Long id) {
        chapterDao.deleteById(id);
    }

    /**
     * 解析当前请求归属的 novelId。
     * <p>优先级:参数显式传入 → NovelContext → 配置兜底。</p>
     */
    private Long resolveNovelId(Long explicit) {
        if (explicit != null) {
            return explicit;
        }
        Long ctxId = NovelContext.getNovelId();
        if (ctxId != null) {
            return ctxId;
        }
        return NovelConstants.DEFAULT_NOVEL_ID;
    }

    /**
     * 历史快照保留策略:仅保留最近 {@value #HISTORY_KEEP_VERSIONS} 版,超出部分删除。
     * <p>优化清单 #60:避免历史快照无限增长,每次保存后清理旧版本。</p>
     *
     * @param chapterId 章节 ID
     */
    private void pruneHistorySnapshots(Long chapterId) {
        try {
            int deleted = historyDao.deleteOldSnapshotsByChapterId(chapterId, HISTORY_KEEP_VERSIONS);
            if (deleted > 0) {
                log.info("[pruneHistory] chapterId={}, deleted={}, keep={}",
                        chapterId, deleted, HISTORY_KEEP_VERSIONS);
            }
        } catch (Exception ex) {
            log.warn("[pruneHistory] failed to prune snapshots for chapterId={}: {}",
                    chapterId, ex.getMessage());
        }
    }
}

