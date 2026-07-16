package ink.realm.config.security;

/**
 * 角色常量(角色收敛为管理员 / 普通用户两类)。
 *
 * <p>命名与数据库 {@code roles.name} 一致(带 {@code ROLE_} 前缀),
 * 与 Spring Security {@code hasRole("ADMIN")} 的隐式去前缀逻辑保持一致。</p>
 *
 * @author songshan.li (ID: 17099618)
 */
public final class SecurityRoles {

    /** 管理员:可只读查看全平台小说,不可修改/删除他人小说。 */
    public static final String ADMIN = "ROLE_ADMIN";

    /** 普通用户:仅可操作自己 owner 的小说。 */
    public static final String USER = "ROLE_USER";

    private SecurityRoles() {
    }
}
