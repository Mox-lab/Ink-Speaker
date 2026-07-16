package ink.realm.novel.service;

import ink.realm.novel.domain.vo.ChapterHistoryVo;

import java.util.List;

/**
 * 章节历史快照服务接口(BASE-07)。
 */
public interface ChapterHistoryService {

    /**
     * 列出某章节的全部历史快照。
     *
     * @param chapterId 章节 ID
     * @return 历史快照列表(按时间倒序,不含 content 全文,仅 metadata)
     */
    List<ChapterHistoryVo> listByChapter(Long chapterId);

    /**
     * 取某条历史快照详情(含 content 全文)。
     *
     * @param historyId 历史 ID
     * @return 历史快照详情
     */
    ChapterHistoryVo getHistory(Long historyId);
}
