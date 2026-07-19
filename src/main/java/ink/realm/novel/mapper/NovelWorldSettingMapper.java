package ink.realm.novel.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.realm.novel.domain.entity.NovelWorldSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 世界观设定 DAO。
 * <p>对应表 novel_world_setting。简单单表查询通过 {@code default} 方法 + LambdaQueryWrapper 实现;</p>
 * <p>级联物理删除保留 XML。</p>
 *
 * @author songshan.li
 */
@Mapper
public interface NovelWorldSettingMapper extends BaseMapper<NovelWorldSetting> {

    /** 按小说 ID + 关键词精确查找。 */
    default Optional<NovelWorldSetting> findByNovelIdAndKeyword(Long novelId, String keyword) {
        return Optional.ofNullable(this.selectOne(new LambdaQueryWrapper<NovelWorldSetting>()
                .eq(NovelWorldSetting::getNovelId, novelId)
                .eq(NovelWorldSetting::getKeyword, keyword)));
    }

    /** 列出某本小说的全部设定。 */
    default List<NovelWorldSetting> listByNovelId(Long novelId) {
        return this.selectList(new LambdaQueryWrapper<NovelWorldSetting>()
                .eq(NovelWorldSetting::getNovelId, novelId)
                .orderByAsc(NovelWorldSetting::getId));
    }

    /** 按分类筛选。 */
    default List<NovelWorldSetting> listByNovelIdAndCategory(Long novelId, String category) {
        return this.selectList(new LambdaQueryWrapper<NovelWorldSetting>()
                .eq(NovelWorldSetting::getNovelId, novelId)
                .eq(NovelWorldSetting::getCategory, category)
                .orderByAsc(NovelWorldSetting::getId));
    }

    /** 关键词模糊匹配(ILIKE,大小写不敏感)。 */
    default List<NovelWorldSetting> searchByNovelIdAndKeywordContaining(Long novelId, String keyword) {
        return this.selectList(new LambdaQueryWrapper<NovelWorldSetting>()
                .eq(NovelWorldSetting::getNovelId, novelId)
                .apply("keyword ILIKE CONCAT('%', {0}, '%')", keyword));
    }

    /** 关键词模糊匹配(ILIKE)+ 分类筛选(设定集「人物」检索用)。 */
    default List<NovelWorldSetting> searchByNovelIdAndKeywordContainingAndCategory(Long novelId, String keyword, String category) {
        return this.selectList(new LambdaQueryWrapper<NovelWorldSetting>()
                .eq(NovelWorldSetting::getNovelId, novelId)
                .eq(NovelWorldSetting::getCategory, category)
                .apply("keyword ILIKE CONCAT('%', {0}, '%')", keyword));
    }

    /** 级联删除:物理删除指定小说的全部世界观设定。 */
    int deleteByNovelId(@Param("novelId") Long novelId);

    /**
     * 物理删除同 (novel_id, keyword) 的全部行(含逻辑删除残留)。
     * <p>用于"删除后重新插入同名设定"场景:逻辑删除仅置 is_del=1,行仍占用唯一键,
     * 重新插入会触发 {@code novel_world_setting_novel_id_keyword_key} 唯一约束冲突。
     * 该方法为原生 SQL,绕过 MyBatis-Plus 的逻辑删除拦截器,真正移除旧行以腾出唯一键。</p>
     */
    int physicallyDeleteByNovelIdAndKeyword(@Param("novelId") Long novelId, @Param("keyword") String keyword);

    /**
     * 物理删除指定主键的世界观设定行。
     * <p>用于用户主动删除单条设定:逻辑删除仅置 is_del=1,行仍占用
     * {@code (novel_id, keyword)} 唯一键,导致同名设定无法重新创建。
     * 该方法为原生 SQL,绕过 MyBatis-Plus 的逻辑删除拦截器,真正移除旧行。</p>
     */
    int physicallyDeleteById(@Param("id") Long id);
}
