package ink.realm.novel.controller;

import ink.realm.common.context.NovelContext;
import ink.realm.common.result.Result;
import ink.realm.novel.domain.dto.OutlineActivateRequest;
import ink.realm.novel.domain.dto.OutlineSaveRequest;
import ink.realm.novel.service.OutlineService;
import ink.realm.novel.domain.vo.OutlineActiveVo;
import ink.realm.novel.domain.vo.OutlineDetailVo;
import ink.realm.novel.domain.vo.OutlineSaveResultVo;
import ink.realm.novel.domain.vo.OutlineSummaryVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 大纲 CRUD 接口(多版本)。
 * <p>统一基址 /api/data/outline,提供:列出版本 / 查全文 / 取激活版本 / 保存新版本 / 激活旧版本 / 删除。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/data/outline")
@RequiredArgsConstructor
@Tag(name = "Outline", description = "大纲 CRUD 接口(多版本)")
public class OutlineController {

    private final OutlineService outlineService;

    /** 列出某小说的全部大纲版本(最新在前)。 */
    @Operation(summary = "列出大纲版本")
    @GetMapping
    public Result<List<OutlineSummaryVo>> listOutlines() {
        // novelId 由 X-Novel-Id 头经 NovelContext 注入,详见 SettingController 说明
        return Result.success(outlineService.listOutlines(NovelContext.getNovelId()));
    }

    /** 获取某版本大纲全文。 */
    @Operation(summary = "获取大纲详情")
    @GetMapping("/detail/{id}")
    public Result<OutlineDetailVo> getOutline(
            @Parameter(description = "大纲 ID") @PathVariable Long id) {
        return Result.success(outlineService.getOutline(id));
    }

    /** 获取当前激活版本(用于续生时取上一版本尾段)。 */
    @Operation(summary = "获取激活版本")
    @GetMapping("/active")
    public Result<OutlineActiveVo> getActiveOutline() {
        return outlineService.getActiveOutline(NovelContext.getNovelId())
                .map(Result::success)
                .orElseGet(Result::success);
    }

    /** 保存大纲(新建版本)。 */
    @Operation(summary = "保存大纲(新建版本)")
    @PostMapping("/save")
    public Result<OutlineSaveResultVo> saveOutline(@RequestBody @Valid OutlineSaveRequest request) {
        OutlineSaveRequest req = request.novelId() != null ? request
                : new OutlineSaveRequest(NovelContext.getNovelId(),
                request.title(), request.theme(), request.chapters(), request.content());
        return Result.success(outlineService.saveOutline(req));
    }

    /** 激活某历史版本。 */
    @Operation(summary = "激活历史版本")
    @PostMapping("/activate")
    public Result<Boolean> activateOutline(@RequestBody @Valid OutlineActivateRequest request) {
        OutlineActivateRequest req = request.novelId() != null ? request
                : new OutlineActivateRequest(request.id(), NovelContext.getNovelId());
        return Result.success(outlineService.activateOutline(req));
    }

    /** 删除某版本。 */
    @Operation(summary = "删除大纲版本")
    @DeleteMapping("/{id}")
    public Result<Void> deleteOutline(
            @Parameter(description = "大纲 ID") @PathVariable Long id) {
        outlineService.deleteOutline(id);
        return Result.success();
    }
}
