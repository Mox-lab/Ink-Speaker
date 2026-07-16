package ink.realm.auth.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.realm.auth.domain.entity.Role;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

/**
 * 角色 DAO。
 * <p>对应表 roles。简单单表查询通过 {@code default} 方法 + LambdaQueryWrapper 实现,
 * 减少手写 SQL;MyBatis-Plus 会自动追加逻辑删除过滤(is_del = 0)。</p>
 *
 * @author songshan.li
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    /** 按角色名精确查找。 */
    default Optional<Role> findByName(String name) {
        return Optional.ofNullable(this.selectOne(
                new LambdaQueryWrapper<Role>().eq(Role::getName, name)));
    }

    /** 检查角色名是否已存在。 */
    default long countByName(String name) {
        return this.selectCount(
                new LambdaQueryWrapper<Role>().eq(Role::getName, name));
    }
}
