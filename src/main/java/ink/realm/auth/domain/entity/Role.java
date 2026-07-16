package ink.realm.auth.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import ink.realm.common.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 角色实体。
 * <p>对应表 roles,角色名以 ROLE_ 前缀(如 ROLE_ADMIN、ROLE_USER)。</p>
 *
 * @author songshan.li
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "角色实体")
@TableName(value = "sys_roles")
public class Role extends BaseEntity {

    /** 角色名,唯一且非空。 */
    @Schema(description = "角色名(如 ROLE_ADMIN / ROLE_USER)")
    private String name;
}
