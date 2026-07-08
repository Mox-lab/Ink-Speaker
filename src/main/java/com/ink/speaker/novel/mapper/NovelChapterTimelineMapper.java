package com.ink.speaker.novel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ink.speaker.novel.domain.entity.NovelChapterTimeline;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 章节时间线 DAO。
 * <p>对应表 novel_chapter_timeline,SQL 见 resources/mapper/NovelChapterTimelineDao.xml。</p>
 */
@Mapper
public interface NovelChapterTimelineMapper extends BaseMapper<NovelChapterTimeline> {

    /**
     * 按章节序号精确查找。
     *
     * @param novelId   小说 ID
     * @param chapterNo 章节序号
     * @return 该章时间线节点(可能为空)
     */
    Optional<NovelChapterTimeline> findByNovelIdAndChapterNo(@Param("novelId") Long novelId,
                                                             @Param("chapterNo") Integer chapterNo);

    /**
     * 列出某本小说的全部章节(按章节序号升序)。
     *
     * @param novelId 小说 ID
     * @return 章节列表
     */
    List<NovelChapterTimeline> listByNovelIdOrderByChapterNoAsc(@Param("novelId") Long novelId);

    /**
     * 按标题或摘要模糊查找。
     *
     * @param novelId       小说 ID
     * @param titleKeyword  标题关键词
     * @param summaryKeyword 摘要关键词
     * @return 匹配的章节列表
     */
    List<NovelChapterTimeline> searchByTitleOrSummary(@Param("novelId") Long novelId,
                                                      @Param("titleKeyword") String titleKeyword,
                                                      @Param("summaryKeyword") String summaryKeyword);

    /**
     * 取最近 N 章(按章节序号倒序,limit N)。
     *
     * @param novelId 小说 ID
     * @param limit   返回条数
     * @return 最近的章节列表(按章节序号升序返回,便于上层直接拼接)
     */
    List<NovelChapterTimeline> findRecentChapters(@Param("novelId") Long novelId,
                                                  @Param("limit") int limit);
}
