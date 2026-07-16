package ink.realm.auth.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 用户-角色名映射行(管理后台批量列举用户角色时使用)。
 *
 * @author songshan.li (ID: 17099618)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleName {

    /** 用户 ID。 */
    private Long userId;

    /** 角色名(如 ROLE_ADMIN)。 */
    private String roleName;
}
