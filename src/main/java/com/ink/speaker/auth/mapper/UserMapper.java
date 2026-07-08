package com.ink.speaker.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ink.speaker.auth.domain.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

/**
 * 用户 DAO。
 * <p>对应表 users,SQL 见 resources/mapper/UserDao.xml。</p>
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 按用户名精确查找。
     *
     * @param username 用户名
     * @return 用户实体(可能为空)
     */
    Optional<User> findByUsername(@Param("username") String username);

    /**
     * 按用户名查找并同时 fetch 角色(避免懒加载 N+1)。
     *
     * @param username 用户名
     * @return 用户实体(角色已加载),可能为空
     */
    Optional<User> findByUsernameWithRoles(@Param("username") String username);

    /**
     * 检查用户名是否已存在。
     *
     * @param username 用户名
     * @return 已存在的记录数(>0 表示存在)
     */
    long countByUsername(@Param("username") String username);
}
