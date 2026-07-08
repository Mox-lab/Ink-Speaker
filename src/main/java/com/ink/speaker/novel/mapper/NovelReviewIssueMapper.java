package com.ink.speaker.novel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ink.speaker.novel.domain.entity.NovelReviewIssue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 章节审查问题 DAO。
 * <p>对应表 novel_review_issue,SQL 见 resources/mapper/NovelReviewIssueDao.xml。</p>
 */
@Mapper
public interface NovelReviewIssueMapper extends BaseMapper<NovelReviewIssue> {

    /**
     * 列出某小说某章的全部审查问题(按严重度降序)。
     *
     * @param novelId   小说 ID
     * @param chapterNo 章节序号
     * @return 审查问题列表
     */
    List<NovelReviewIssue> listByNovelIdAndChapterNoOrderBySeverityDesc(@Param("novelId") Long novelId,
                                                                       @Param("chapterNo") Integer chapterNo);

    /**
     * 列出某小说全部未解决的审查问题(供前端侧栏展示)。
     *
     * @param novelId 小说 ID
     * @param status  状态(open/resolved/ignored)
     * @return 审查问题列表
     */
    List<NovelReviewIssue> listByNovelIdAndStatusOrderByChapterNoAsc(@Param("novelId") Long novelId,
                                                                     @Param("status") String status);

    /**
     * 列出某小说某状态的审查问题。
     *
     * @param novelId 小说 ID
     * @param status  状态
     * @return 审查问题列表
     */
    List<NovelReviewIssue> listByNovelIdAndStatus(@Param("novelId") Long novelId,
                                                  @Param("status") String status);
}
