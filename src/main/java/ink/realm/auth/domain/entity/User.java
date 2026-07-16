package ink.realm.auth.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.realm.common.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * 用户实体。
 * <p>对应表 users,与 Role 的关联通过 user_roles 中间表。</p>
 * <p>roles 由 {@code UserMapper.xml} 的 {@code <collection>} 在查询时填充,
 * 非数据库列,故标注 {@link TableField#exist()} = false。</p>
 *
 * @author songshan.li
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户实体")
@TableName(value = "sys_users")
public class User extends BaseEntity {

    /** 用户名,唯一且非空。 */
    @Schema(description = "用户名(唯一)")
    private String username;

    /** 昵称(唯一,可空;注册后由用户补充,作为小说作者名展示)。 */
    @Schema(description = "昵称(唯一,可空)")
    private String nickname;

    /** 密码(加密存储)。 */
    @Schema(description = "密码(BCrypt 哈希)")
    private String password;

    /** 是否启用,默认 true。 */
    @Schema(description = "是否启用")
    @Builder.Default
    private boolean enabled = true;

    /** 关联角色集合,由 UserMapper 的关联查询填充,非表字段。 */
    @Schema(description = "关联角色集合(由关联查询填充,非表字段)")
    @TableField(exist = false)
    private Set<Role> roles;
}
