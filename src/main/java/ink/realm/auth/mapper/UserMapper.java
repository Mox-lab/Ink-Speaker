package ink.realm.auth.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.realm.auth.domain.entity.User;
import ink.realm.auth.domain.vo.UserRoleName;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

import java.util.Optional;

/**
 * 用户 DAO。
 * <p>对应表 users。简单单表查询(findByUsername / countByUsername)通过 {@code default} 方法
 * + LambdaQueryWrapper 实现;联表 fetch 角色、绑定默认角色等多表操作保留 XML。</p>
 *
 * @author songshan.li
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /** 按用户名精确查找。 */
    default Optional<User> findByUsername(String username) {
        return Optional.ofNullable(this.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)));
    }

    /** 检查用户名是否已存在。 */
    default long countByUsername(String username) {
        return this.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    /**
     * 检查昵称是否已被其他用户占用(排除自身 userId)。
     *
     * @param nickname 待校验昵称
     * @param userId   当前用户 ID(用于排除自身)
     * @return 冲突数量(0 表示可用)
     */
    default long countByNicknameExcluding(String nickname, Long userId) {
        return this.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getNickname, nickname)
                .ne(User::getId, userId));
    }

    /**
     * 按用户名查找并同时 fetch 角色(避免懒加载 N+1,联表查询保留 XML 实现)。
     */
    Optional<User> findByUsernameWithRoles(@Param("username") String username);

    /**
     * 为新注册用户绑定默认角色 ROLE_USER(写入 user_roles 多表操作,保留 XML 实现)。
     */
    int bindDefaultUserRole(@Param("userId") Long userId);

    /**
     * 批量列举用户在指定 ID 集合内的角色名(管理后台一次性聚合,避免 N+1)。
     * @param ids 用户 ID 集合
     * @return 用户-角色名映射行列表
     */
    List<UserRoleName> listUserRoleNames(@Param("ids") List<Long> ids);
}
