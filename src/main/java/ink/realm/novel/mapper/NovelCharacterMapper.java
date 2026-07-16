package ink.realm.novel.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.realm.novel.domain.entity.NovelCharacter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 人物档案 DAO。
 * <p>对应表 novel_character。简单单表查询通过 {@code default} 方法 + LambdaQueryWrapper 实现;</p>
 * <p>级联物理删除保留 XML(原因同 NovelChapterContentMapper)。</p>
 *
 * @author songshan.li
 */
@Mapper
public interface NovelCharacterMapper extends BaseMapper<NovelCharacter> {

    /** 按小说 ID + 姓名精确查找。 */
    default Optional<NovelCharacter> findByNovelIdAndName(Long novelId, String name) {
        return Optional.ofNullable(this.selectOne(new LambdaQueryWrapper<NovelCharacter>()
                .eq(NovelCharacter::getNovelId, novelId)
                .eq(NovelCharacter::getName, name)));
    }

    /** 列出某本小说的全部人物。 */
    default List<NovelCharacter> listByNovelId(Long novelId) {
        return this.selectList(new LambdaQueryWrapper<NovelCharacter>()
                .eq(NovelCharacter::getNovelId, novelId)
                .orderByAsc(NovelCharacter::getId));
    }

    /** 模糊查询姓名(ILIKE,保留大小写不敏感语义)。 */
    default List<NovelCharacter> searchByNovelIdAndNameContaining(Long novelId, String name) {
        return this.selectList(new LambdaQueryWrapper<NovelCharacter>()
                .eq(NovelCharacter::getNovelId, novelId)
                .apply("name ILIKE CONCAT('%', {0}, '%')", name));
    }

    /** 级联删除:物理删除指定小说的全部人物档案。 */
    int deleteByNovelId(@Param("novelId") Long novelId);
}
