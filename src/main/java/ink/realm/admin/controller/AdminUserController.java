package ink.realm.admin.controller;

import ink.realm.admin.domain.dto.AdminUserEnabledRequest;
import ink.realm.admin.domain.vo.AdminUserVo;
import ink.realm.admin.service.AdminService;
import ink.realm.common.result.PageResult;
import ink.realm.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台用户接口(列举 + 启用/禁用)。
 *
 * <p>路由 {@code /api/admin/**} 已被 SecurityConfig 限制为 ROLE_ADMIN。</p>
 *
 * @author songshan.li (ID: 17099618)
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin-User", description = "管理后台用户接口")
public class AdminUserController {

    private final AdminService adminService;

    @Operation(summary = "管理员列出全部用户", description = "分页返回全部用户,含角色")
    @GetMapping
    public Result<PageResult<AdminUserVo>> listUsers(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.success(adminService.listUsers(page, size));
    }

    @Operation(summary = "管理员启用/禁用用户", description = "仅允许修改启用状态,不可改其他字段")
    @PatchMapping("/{id}/enabled")
    public Result<Void> setEnabled(
            @PathVariable Long id,
            @RequestBody @Valid AdminUserEnabledRequest request) {
        adminService.setUserEnabled(id, request.enabled());
        return Result.success();
    }
}
