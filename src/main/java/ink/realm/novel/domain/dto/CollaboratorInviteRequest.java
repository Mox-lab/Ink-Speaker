package ink.realm.novel.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 邀请协作者请求(BASE-11 多用户协作)。
 * <p>通过用户名邀请,不接受 userId(防止枚举攻击)。</p>
 *
 * @param username 被邀请用户的用户名(必填)
 * @param role     协作角色:editor(可编辑) / viewer(只读),必填
 * @author songshan.li
 */
public record CollaboratorInviteRequest(
        @NotBlank String username,
        @NotBlank @Pattern(regexp = "editor|viewer", message = "角色仅支持 editor / viewer") String role
) {
}
