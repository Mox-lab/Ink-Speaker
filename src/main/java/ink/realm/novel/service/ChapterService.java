package ink.realm.novel.service;

import ink.realm.novel.domain.dto.ChapterSaveRequest;
import ink.realm.novel.domain.vo.ChapterDetailVo;
import ink.realm.novel.domain.vo.ChapterSummaryVo;
import ink.realm.novel.domain.vo.SaveResultVo;

import java.util.List;

/**
 * 章节服务接口。
 * <p>负责章节正文的 CRUD,并在保存后异步触发的长期记忆抽取与审查由 Controller 层协调。</p>
 */
public interface ChapterService {

    /**
     * 列出某小说的全部章节(摘要)。
     *
     * @param novelId 小说 ID
     * @return 章节摘要列表(按章节序号升序)
     */
    List<ChapterSummaryVo> listChapters(Long novelId);

    /**
     * 获取章节详情(含全文)。
     *
     * @param id 章节 ID
     * @return 章节详情
     */
    ChapterDetailVo getChapter(Long id);

    /**
     * 保存章节(同 novelId+chapterNo 则覆盖)。
     *
     * @param request 章节保存请求
     * @return 保存后的主键
     */
    SaveResultVo saveChapter(ChapterSaveRequest request);

    /**
     * 删除章节。
     *
     * @param id 章节 ID
     */
    void deleteChapter(Long id);
}
