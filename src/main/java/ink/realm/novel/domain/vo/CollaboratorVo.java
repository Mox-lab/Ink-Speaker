package ink.realm.novel.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 协作者视图对象(BASE-11 多用户协作)。
 * <p>列出某本小说的协作者时,联表带出被邀请用户的用户名供前端展示。</p>
 *
 * @author songshan.li
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollaboratorVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 协作关系 ID。 */
    private Long id;
    /** 所属小说 ID。 */
    private Long novelId;
    /** 被邀请用户 ID。 */
    private Long userId;
    /** 被邀请用户的用户名(来自 sys_users 表 JOIN)。 */
    private String username;
    /** 协作角色:editor / viewer。 */
    private String role;
    /** 邀请时间(对应表 ct_time)。 */
    private LocalDateTime createdAt;
}
