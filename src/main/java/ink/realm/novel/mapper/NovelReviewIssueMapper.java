package ink.realm.novel.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.realm.novel.domain.entity.NovelReviewIssue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 章节审查问题 DAO。
 * <p>对应表 novel_review_issue。简单单表查询通过 {@code default} 方法 + LambdaQueryWrapper 实现;</p>
 * <p>级联物理删除保留 XML。</p>
 *
 * @author songshan.li
 */
@Mapper
public interface NovelReviewIssueMapper extends BaseMapper<NovelReviewIssue> {

    /** 列出某小说某章的全部审查问题(按严重度降序)。 */
    default List<NovelReviewIssue> listByNovelIdAndChapterNoOrderBySeverityDesc(Long novelId, Integer chapterNo) {
        return this.selectList(new LambdaQueryWrapper<NovelReviewIssue>()
                .eq(NovelReviewIssue::getNovelId, novelId)
                .eq(NovelReviewIssue::getChapterNo, chapterNo)
                .orderByDesc(NovelReviewIssue::getSeverity)
                .orderByAsc(NovelReviewIssue::getId));
    }

    /** 列出某小说全部未解决的审查问题(按章节号升序)。 */
    default List<NovelReviewIssue> listByNovelIdAndStatusOrderByChapterNoAsc(Long novelId, String status) {
        return this.selectList(new LambdaQueryWrapper<NovelReviewIssue>()
                .eq(NovelReviewIssue::getNovelId, novelId)
                .eq(NovelReviewIssue::getStatus, status)
                .orderByAsc(NovelReviewIssue::getChapterNo)
                .orderByAsc(NovelReviewIssue::getId));
    }

    /** 列出某小说某状态的审查问题。 */
    default List<NovelReviewIssue> listByNovelIdAndStatus(Long novelId, String status) {
        return this.selectList(new LambdaQueryWrapper<NovelReviewIssue>()
                .eq(NovelReviewIssue::getNovelId, novelId)
                .eq(NovelReviewIssue::getStatus, status)
                .orderByAsc(NovelReviewIssue::getChapterNo)
                .orderByAsc(NovelReviewIssue::getId));
    }

    /** 级联删除:物理删除指定小说的全部审查问题。 */
    int deleteByNovelId(@Param("novelId") Long novelId);
}
