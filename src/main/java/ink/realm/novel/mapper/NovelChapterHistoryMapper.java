package ink.realm.novel.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.realm.novel.domain.entity.NovelChapterHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 章节历史快照 DAO(BASE-07)。
 * <p>简单单表查询通过 {@code default} 方法 + LambdaQueryWrapper 实现;</p>
 * <p>保留最近 N 版删除(deleteOldSnapshotsByChapterId 用 OFFSET 子查询,保留 XML)。</p>
 *
 * @author songshan.li
 */
@Mapper
public interface NovelChapterHistoryMapper extends BaseMapper<NovelChapterHistory> {

    /** 列出某章节的全部历史快照(按 id 倒序)。 */
    default List<NovelChapterHistory> listByChapterIdOrderByIdDesc(Long chapterId) {
        return this.selectList(new LambdaQueryWrapper<NovelChapterHistory>()
                .eq(NovelChapterHistory::getChapterId, chapterId)
                .orderByDesc(NovelChapterHistory::getId));
    }

    /** 列出某小说某章的全部历史快照(按 id 倒序)。 */
    default List<NovelChapterHistory> listByNovelIdAndChapterNoOrderByIdDesc(Long novelId, Integer chapterNo) {
        return this.selectList(new LambdaQueryWrapper<NovelChapterHistory>()
                .eq(NovelChapterHistory::getNovelId, novelId)
                .eq(NovelChapterHistory::getChapterNo, chapterNo)
                .orderByDesc(NovelChapterHistory::getId));
    }

    /** 删除某章节超出 keep 数量的旧历史快照(OFFSET 子查询,保留 XML)。 */
    int deleteOldSnapshotsByChapterId(@Param("chapterId") Long chapterId, @Param("keep") int keep);
}
