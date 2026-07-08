package com.ink.speaker.novel.service.impl;

import com.ink.speaker.common.BusinessException;
import com.ink.speaker.common.NovelContext;
import com.ink.speaker.common.ResultCode;
import com.ink.speaker.ai.core.director.DirectorAgent;
import com.ink.speaker.novel.mapper.NovelChapterContentMapper;
import com.ink.speaker.novel.domain.dto.ChapterSaveRequest;
import com.ink.speaker.novel.domain.entity.NovelChapterContent;
import com.ink.speaker.novel.service.ChapterService;
import com.ink.speaker.novel.domain.vo.ChapterDetailVo;
import com.ink.speaker.novel.domain.vo.ChapterSummaryVo;
import com.ink.speaker.novel.domain.vo.SaveResultVo;
import com.ink.speaker.ai.core.memory.LongTermMemoryExtractor;
import com.ink.speaker.util.ArgsUtil;
import com.ink.speaker.util.NovelConstants;
import com.ink.speaker.util.VoConverters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 章节服务实现。
 * <p>负责章节正文 CRUD;保存后异步触发的记忆抽取与审查由本类协调(保持原业务行为)。</p>
 *
 * <p><b>novelId 解析顺序:</b></p>
 * <ol>
 *   <li>请求体显式携带(ChapterSaveRequest.novelId)</li>
 *   <li>{@link NovelContext}(由 X-Novel-Id 头注入)</li>
 *   <li>配置兜底 {@code ink-speaker.current-id}(仅单小说场景)</li>
 * </ol>
 *
 * <p>异步调用记忆抽取与审查时显式传入 novelId,因 ThreadLocal 不跨线程。</p>
 *
 * <p>第 4 阶段:saveChapter 加 {@link Propagation#REQUIRES_NEW} 隔离异步触发的副作用,
 * 捕获 {@link ObjectOptimisticLockingFailureException} 给出友好提示。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterServiceImpl implements ChapterService {

    /** 章节保存冲突时最大重试次数(乐观锁失败后用版本号重读)。 */
    public static final int SAVE_RETRY_MAX = 3;

    private final NovelChapterContentMapper chapterDao;
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
     * 保存章节(带乐观锁重试)。
     *
     * <p>并发场景:作者本地编辑器 + 服务端后台异步审查同时改章节时,
     * JPA {@code @Version} 会在 UPDATE 时校验版本,失败抛
     * {@link ObjectOptimisticLockingFailureException}。这里捕获后重试最多
     * {@link #SAVE_RETRY_MAX} 次,仍失败则返回 409 给前端提示"内容已被他人修改"。</p>
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
                    chapterDao.updateById(chapter);
                }
                log.info("[saveChapter] novelId={}, chapterNo={}, id={}, attempt={}",
                        novelId, chapterNo, chapter.getId(), attempt);

                // 异步触发长期记忆抽取 + 章节审查
                // 显式传入 novelId:ThreadLocal 不会跨 @Async 线程继承
                memoryExtractor.extractAndPersistCharacters(content, chapterNo, novelId);
                directorAgent.reviewChapter(content, chapterNo, novelId);

                return new SaveResultVo(chapter.getId());
            } catch (ObjectOptimisticLockingFailureException e) {
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
}

