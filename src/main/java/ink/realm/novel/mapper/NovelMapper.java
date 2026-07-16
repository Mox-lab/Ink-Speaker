package ink.realm.novel.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.realm.novel.domain.entity.Novel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 小说主表 DAO。
 * <p>对应表 novel。简单单表查询通过 {@code default} 方法 + LambdaQueryWrapper 实现;</p>
 * <p>物理删除(deleteByIdAndOwner)与跨表统计(count*)保留 XML。</p>
 *
 * @author songshan.li
 */
@Mapper
public interface NovelMapper extends BaseMapper<Novel> {

    /** 按标题精确查找。 */
    default Optional<Novel> findByTitle(String title) {
        return Optional.ofNullable(this.selectOne(
                new LambdaQueryWrapper<Novel>().eq(Novel::getTitle, title)));
    }

    /** 列出指定用户拥有的全部小说(R5 用户隔离)。 */
    default List<Novel> listByOwnerId(Long ownerId) {
        return this.selectList(new LambdaQueryWrapper<Novel>()
                .eq(Novel::getOwnerId, ownerId)
                .orderByDesc(Novel::getUtTime));
    }

    /** 按 id 查询并校验所有权(R5 用户隔离)。 */
    default Optional<Novel> findByIdAndOwner(Long id, Long ownerId) {
        return Optional.ofNullable(this.selectOne(new LambdaQueryWrapper<Novel>()
                .eq(Novel::getId, id).eq(Novel::getOwnerId, ownerId)));
    }

    /** 列出所有公开到公共参考池的小说(R5 跨小说参考)。 */
    default List<Novel> listSharedForReference() {
        return this.selectList(new LambdaQueryWrapper<Novel>()
                .eq(Novel::isSharedForReference, true)
                .orderByDesc(Novel::getUtTime));
    }

    /** 按 id 查询并校验是否公开到公共参考池(BASE-09)。 */
    default Optional<Novel> findByIdAndShared(Long id) {
        return Optional.ofNullable(this.selectOne(new LambdaQueryWrapper<Novel>()
                .eq(Novel::getId, id).eq(Novel::isSharedForReference, true)));
    }

    /** 按 id 物理删除小说(R5 用户隔离;逻辑删除不适用,保留 XML)。 */
    int deleteByIdAndOwner(@Param("id") Long id, @Param("ownerId") Long ownerId);

    /** 统计某本小说的章节数(排除逻辑删除)。 */
    int countChapters(@Param("novelId") Long novelId);

    /** 统计某本小说的大纲版本数(排除逻辑删除)。 */
    int countOutlines(@Param("novelId") Long novelId);

    /** 统计某本小说的人物档案数(排除逻辑删除)。 */
    int countCharacters(@Param("novelId") Long novelId);

    /** 统计某本小说的世界观设定数(排除逻辑删除)。 */
    int countSettings(@Param("novelId") Long novelId);

    /** 统计某本小说的未解决审查问题数(排除逻辑删除)。 */
    int countReviewIssuesByStatus(@Param("novelId") Long novelId, @Param("status") String status);
}
