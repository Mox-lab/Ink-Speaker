package ink.realm.novel.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 修改协作者角色请求(BASE-11 多用户协作)。
 *
 * @param role 新角色:editor / viewer,必填
 * @author songshan.li
 */
public record CollaboratorUpdateRequest(
        @NotBlank @Pattern(regexp = "editor|viewer", message = "角色仅支持 editor / viewer") String role
) {
}
