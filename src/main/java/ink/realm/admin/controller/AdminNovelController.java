package ink.realm.admin.controller;

import ink.realm.admin.domain.vo.AdminNovelVo;
import ink.realm.admin.service.AdminService;
import ink.realm.common.result.PageResult;
import ink.realm.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台小说接口(只读列举全平台小说)。
 *
 * <p>路由 {@code /api/admin/**} 已被 SecurityConfig 限制为 ROLE_ADMIN。</p>
 *
 * @author songshan.li (ID: 17099618)
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/novels")
@RequiredArgsConstructor
@Tag(name = "Admin-Novel", description = "管理后台小说接口(只读)")
public class AdminNovelController {

    private final AdminService adminService;

    @Operation(summary = "管理员列出全平台小说", description = "分页返回全部用户的小说,只读")
    @GetMapping
    public Result<PageResult<AdminNovelVo>> listNovels(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.success(adminService.listNovels(page, size));
    }
}
