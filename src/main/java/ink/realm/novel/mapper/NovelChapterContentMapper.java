package ink.realm.novel.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.realm.novel.domain.entity.NovelChapterContent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 章节正文 DAO。
 * <p>对应表 novel_chapter_content。简单单表查询通过 {@code default} 方法 + LambdaQueryWrapper 实现;</p>
 * <p>级联物理删除(deleteByNovelId)保留 XML,因 {@code @TableLogic} 会让 MyBatis-Plus 的
 * delete 变成逻辑删除,不符级联清理语义。</p>
 *
 * @author songshan.li
 */
@Mapper
public interface NovelChapterContentMapper extends BaseMapper<NovelChapterContent> {

    /** 列出某小说的全部章节,按章节序号升序。 */
    default List<NovelChapterContent> listByNovelIdOrderByChapterNoAsc(Long novelId) {
        return this.selectList(new LambdaQueryWrapper<NovelChapterContent>()
                .eq(NovelChapterContent::getNovelId, novelId)
                .orderByAsc(NovelChapterContent::getChapterNo));
    }

    /** 取某小说某章(唯一)。 */
    default Optional<NovelChapterContent> findByNovelIdAndChapterNo(Long novelId, Integer chapterNo) {
        return Optional.ofNullable(this.selectOne(new LambdaQueryWrapper<NovelChapterContent>()
                .eq(NovelChapterContent::getNovelId, novelId)
                .eq(NovelChapterContent::getChapterNo, chapterNo)));
    }

    /** 取最大章节序号(用于续写时确定下一章号)。 */
    default Optional<NovelChapterContent> findFirstByNovelIdOrderByChapterNoDesc(Long novelId) {
        return Optional.ofNullable(this.selectOne(new LambdaQueryWrapper<NovelChapterContent>()
                .eq(NovelChapterContent::getNovelId, novelId)
                .orderByDesc(NovelChapterContent::getChapterNo)
                .last("LIMIT 1")));
    }

    /** 级联删除:物理删除指定小说的全部章节。 */
    int deleteByNovelId(@Param("novelId") Long novelId);
}
