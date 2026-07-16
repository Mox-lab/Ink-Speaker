package ink.realm.auth.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 昵称更新请求 DTO。
 *
 * @param nickname 昵称(2-20 字符,全局唯一;注册后由用户补充,作为小说作者名展示)
 */
public record UpdateNicknameRequest(
        @NotBlank
        @Size(min = 2, max = 20)
        String nickname) {
}
