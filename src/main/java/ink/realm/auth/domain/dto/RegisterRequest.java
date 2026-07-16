package ink.realm.auth.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求 DTO。
 *
 * @param username         用户名(3-50 字符,字母/数字/下划线)
 * @param password         密码(6-50 字符)
 * @param confirmPassword  确认密码(必须与 password 一致)
 */
public record RegisterRequest(
        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "用户名仅支持字母、数字与下划线")
        String username,

        @NotBlank
        @Size(min = 6, max = 50)
        String password,

        @NotBlank
        String confirmPassword) {
}
