package ink.realm.novel.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.realm.novel.domain.entity.NovelChapterTimeline;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 章节时间线 DAO。
 * <p>对应表 novel_chapter_timeline。简单单表查询通过 {@code default} 方法 + LambdaQueryWrapper 实现;</p>
 * <p>级联物理删除保留 XML。</p>
 *
 * @author songshan.li
 */
@Mapper
public interface NovelChapterTimelineMapper extends BaseMapper<NovelChapterTimeline> {

    /** 按章节序号精确查找。 */
    default Optional<NovelChapterTimeline> findByNovelIdAndChapterNo(Long novelId, Integer chapterNo) {
        return Optional.ofNullable(this.selectOne(new LambdaQueryWrapper<NovelChapterTimeline>()
                .eq(NovelChapterTimeline::getNovelId, novelId)
                .eq(NovelChapterTimeline::getChapterNo, chapterNo)));
    }

    /** 列出某本小说的全部章节(按章节序号升序)。 */
    default List<NovelChapterTimeline> listByNovelIdOrderByChapterNoAsc(Long novelId) {
        return this.selectList(new LambdaQueryWrapper<NovelChapterTimeline>()
                .eq(NovelChapterTimeline::getNovelId, novelId)
                .orderByAsc(NovelChapterTimeline::getChapterNo));
    }

    /** 按标题或摘要模糊查找(ILIKE,大小写不敏感)。 */
    default List<NovelChapterTimeline> searchByTitleOrSummary(Long novelId, String titleKeyword, String summaryKeyword) {
        return this.selectList(new LambdaQueryWrapper<NovelChapterTimeline>()
                .eq(NovelChapterTimeline::getNovelId, novelId)
                .and(w -> w.apply("title ILIKE CONCAT('%', {0}, '%')", titleKeyword)
                        .or()
                        .apply("summary ILIKE CONCAT('%', {0}, '%')", summaryKeyword)));
    }

    /**
     * 取最近 N 章:取章节序号最大的 N 条,再按序号升序返回(与原 SQL 语义一致)。
     */
    default List<NovelChapterTimeline> findRecentChapters(Long novelId, int limit) {
        List<NovelChapterTimeline> recent = this.selectList(new LambdaQueryWrapper<NovelChapterTimeline>()
                .eq(NovelChapterTimeline::getNovelId, novelId)
                .orderByDesc(NovelChapterTimeline::getChapterNo)
                .last("LIMIT " + limit));
        Collections.reverse(recent);
        return recent;
    }

    /** 级联删除:物理删除指定小说的全部时间线节点。 */
    int deleteByNovelId(@Param("novelId") Long novelId);
}
