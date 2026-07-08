package com.ink.speaker.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ink.speaker.auth.domain.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

/**
 * 角色 DAO。
 * <p>对应表 roles,SQL 见 resources/mapper/RoleDao.xml。</p>
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    /**
     * 按角色名精确查找。
     *
     * @param name 角色名(如 ROLE_ADMIN)
     * @return 角色,可能为空
     */
    Optional<Role> findByName(@Param("name") String name);

    /**
     * 检查角色名是否已存在。
     *
     * @param name 角色名
     * @return 已存在的记录数(>0 表示存在)
     */
    long countByName(@Param("name") String name);
}
