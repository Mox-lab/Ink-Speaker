package ink.realm.novel.service.impl;

import ink.realm.common.exception.BusinessException;
import ink.realm.common.result.ResultCode;
import ink.realm.novel.domain.entity.NovelChapterHistory;
import ink.realm.novel.domain.vo.ChapterHistoryVo;
import ink.realm.novel.mapper.NovelChapterHistoryMapper;
import ink.realm.novel.service.ChapterHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 章节历史快照服务实现(BASE-07)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterHistoryServiceImpl implements ChapterHistoryService {

    private final NovelChapterHistoryMapper historyDao;

    @Override
    public List<ChapterHistoryVo> listByChapter(Long chapterId) {
        if (chapterId == null) {
            return List.of();
        }
        return historyDao.listByChapterIdOrderByIdDesc(chapterId).stream()
                .map(this::toVo)
                .toList();
    }

    @Override
    public ChapterHistoryVo getHistory(Long historyId) {
        NovelChapterHistory h = historyDao.selectById(historyId);
        if (h == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "历史快照不存在: " + historyId);
        }
        return toVo(h);
    }

    private ChapterHistoryVo toVo(NovelChapterHistory h) {
        return ChapterHistoryVo.builder()
                .id(h.getId())
                .chapterId(h.getChapterId())
                .chapterNo(h.getChapterNo())
                .title(h.getTitle())
                .content(h.getContent())
                .wordCount(h.getWordCount())
                .sessionId(h.getSessionId())
                .snapshotVersion(h.getSnapshotVersion())
                .createdAt(h.getCtTime())
                .build();
    }
}
