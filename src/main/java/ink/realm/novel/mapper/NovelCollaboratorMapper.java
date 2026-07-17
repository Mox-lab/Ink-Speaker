package ink.realm.novel.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.realm.novel.domain.entity.NovelCollaborator;
import ink.realm.novel.domain.vo.CollaboratorVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 小说协作者 DAO(BASE-11 多用户协作)。
 * <p>对应表 novel_collaborator。简单单表查询通过 {@code default} 方法 + LambdaQueryWrapper 实现;</p>
 * <p>联表带出用户名的 {@code listByNovelId} 保留 XML 实现。</p>
 *
 * @author songshan.li
 */
@Mapper
public interface NovelCollaboratorMapper extends BaseMapper<NovelCollaborator> {

    /** 列出某本小说的全部协作者,联表带出被邀请用户的用户名(供前端展示)。 */
    List<CollaboratorVo> listByNovelId(@Param("novelId") Long novelId);

    /** 反查某用户协作的全部小说 ID(BASE-11 小说列表分区)。 */
    default List<Long> listNovelIdsByUserId(Long userId) {
        return this.selectList(new LambdaQueryWrapper<NovelCollaborator>()
                        .eq(NovelCollaborator::getUserId, userId)
                        .select(NovelCollaborator::getNovelId))
                .stream()
                .map(NovelCollaborator::getNovelId)
                .toList();
    }

    /** 按 (小说ID, 用户ID) 查找协作关系,用于去重与权限判断(无则 null)。 */
    default NovelCollaborator findByNovelIdAndUserId(Long novelId, Long userId) {
        return this.selectOne(new LambdaQueryWrapper<NovelCollaborator>()
                .eq(NovelCollaborator::getNovelId, novelId)
                .eq(NovelCollaborator::getUserId, userId));
    }
}
