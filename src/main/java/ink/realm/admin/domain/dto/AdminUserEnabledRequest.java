package ink.realm.admin.domain.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 管理员启用/禁用用户请求。
 *
 * @param enabled true=启用,false=禁用
 */
public record AdminUserEnabledRequest(
        @NotNull Boolean enabled
) {
}
