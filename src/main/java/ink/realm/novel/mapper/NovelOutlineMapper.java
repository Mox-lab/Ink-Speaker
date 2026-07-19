package ink.realm.novel.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.realm.novel.domain.entity.NovelOutline;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 大纲 DAO。
 * <p>对应表 novel_outline。简单单表查询通过 {@code default} 方法 + LambdaQueryWrapper 实现;</p>
 * <p>激活标记切换(clearActiveFlag / updateActive)用 LambdaUpdateWrapper;</p>
 * <p>级联物理删除保留 XML。</p>
 *
 * @author songshan.li
 */
@Mapper
public interface NovelOutlineMapper extends BaseMapper<NovelOutline> {

    /** 列出某小说的全部大纲,按版本倒序(最新在前)。 */
    default List<NovelOutline> listByNovelIdOrderByVersionDesc(Long novelId) {
        return this.selectList(new LambdaQueryWrapper<NovelOutline>()
                .eq(NovelOutline::getNovelId, novelId)
                .orderByDesc(NovelOutline::getVersion));
    }

    /** 当前激活版本。 */
    default Optional<NovelOutline> findByNovelIdAndActiveTrue(Long novelId) {
        return Optional.ofNullable(this.selectOne(new LambdaQueryWrapper<NovelOutline>()
                .eq(NovelOutline::getNovelId, novelId)
                .eq(NovelOutline::isActive, true)));
    }

    /** 最新版本号(取最大版本;含逻辑删除行,避免删除后版本号复用触发唯一约束冲突)。 */
    default Integer findMaxVersion(Long novelId) {
        return selectMaxVersionIncludingDeleted(novelId);
    }

    /** 取最大版本号(含逻辑删除行),避免删除后版本号复用导致唯一约束冲突。 */
    Integer selectMaxVersionIncludingDeleted(@Param("novelId") Long novelId);

    /** 切换激活版本前,先把同小说其他版本全部置为 inactive。 */
    default int clearActiveFlag(Long novelId) {
        return this.update(null, new LambdaUpdateWrapper<NovelOutline>()
                .eq(NovelOutline::getNovelId, novelId)
                .set(NovelOutline::isActive, false));
    }

    /** 激活指定版本。 */
    default int updateActive(Long id, boolean active) {
        return this.update(null, new LambdaUpdateWrapper<NovelOutline>()
                .eq(NovelOutline::getId, id)
                .set(NovelOutline::isActive, active));
    }

    /** 级联删除:物理删除指定小说的全部大纲版本。 */
    int deleteByNovelId(@Param("novelId") Long novelId);
}
