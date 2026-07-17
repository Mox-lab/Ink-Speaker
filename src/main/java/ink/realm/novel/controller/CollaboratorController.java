package ink.realm.novel.controller;

import ink.realm.common.result.Result;
import ink.realm.novel.domain.dto.CollaboratorInviteRequest;
import ink.realm.novel.domain.dto.CollaboratorUpdateRequest;
import ink.realm.novel.domain.vo.CollaboratorVo;
import ink.realm.novel.service.CollaboratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小说协作者管理接口(BASE-11 多用户协作)。
 * <p>所有端点仅对小说 owner 开放,非 owner 调用抛 FORBIDDEN。</p>
 *
 * @author songshan.li
 */
@Slf4j
@RestController
@RequestMapping("/api/data/novel")
@RequiredArgsConstructor
@Tag(name = "Collaborator", description = "小说协作者管理接口")
public class CollaboratorController {

    private final CollaboratorService collaboratorService;

    /** 列出某本小说的全部协作者(仅 owner)。 */
    @Operation(summary = "列出协作者", description = "返回该小说全部协作者(含用户名与角色)")
    @GetMapping("/{novelId}/collaborators")
    public Result<List<CollaboratorVo>> listCollaborators(@PathVariable Long novelId) {
        return Result.success(collaboratorService.listByNovelId(novelId));
    }

    /** 邀请协作者(仅 owner)。 */
    @Operation(summary = "邀请协作者", description = "通过用户名邀请,角色 editor / viewer")
    @PostMapping("/{novelId}/collaborators")
    public Result<CollaboratorVo> invite(@PathVariable Long novelId,
                                         @Valid @RequestBody CollaboratorInviteRequest request) {
        return Result.success(collaboratorService.invite(novelId, request));
    }

    /** 修改协作者角色(仅 owner)。 */
    @Operation(summary = "修改协作者角色", description = "editor ↔ viewer")
    @PatchMapping("/{novelId}/collaborators/{id}")
    public Result<Void> updateRole(@PathVariable Long novelId,
                                   @PathVariable Long id,
                                   @Valid @RequestBody CollaboratorUpdateRequest request) {
        collaboratorService.updateRole(novelId, id, request);
        return Result.success();
    }

    /** 移除协作者(仅 owner)。 */
    @Operation(summary = "移除协作者", description = "逻辑删除协作关系")
    @DeleteMapping("/{novelId}/collaborators/{id}")
    public Result<Void> remove(@PathVariable Long novelId, @PathVariable Long id) {
        collaboratorService.remove(novelId, id);
        return Result.success();
    }
}
