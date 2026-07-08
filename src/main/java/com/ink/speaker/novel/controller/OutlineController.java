package com.ink.speaker.novel.controller;

import com.ink.speaker.common.Result;
import com.ink.speaker.novel.domain.dto.OutlineActivateRequest;
import com.ink.speaker.novel.domain.dto.OutlineSaveRequest;
import com.ink.speaker.novel.service.OutlineService;
import com.ink.speaker.novel.domain.vo.OutlineActiveVo;
import com.ink.speaker.novel.domain.vo.OutlineDetailVo;
import com.ink.speaker.novel.domain.vo.OutlineSaveResultVo;
import com.ink.speaker.novel.domain.vo.OutlineSummaryVo;
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
    @GetMapping("/{novelId}")
    public Result<List<OutlineSummaryVo>> listOutlines(
            @Parameter(description = "小说 ID") @PathVariable Long novelId) {
        return Result.success(outlineService.listOutlines(novelId));
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
    @GetMapping("/active/{novelId}")
    public Result<OutlineActiveVo> getActiveOutline(
            @Parameter(description = "小说 ID") @PathVariable Long novelId) {
        return outlineService.getActiveOutline(novelId)
                .map(Result::success)
                .orElseGet(Result::success);
    }

    /** 保存大纲(新建版本)。 */
    @Operation(summary = "保存大纲(新建版本)")
    @PostMapping("/save")
    public Result<OutlineSaveResultVo> saveOutline(@RequestBody @Valid OutlineSaveRequest request) {
        return Result.success(outlineService.saveOutline(request));
    }

    /** 激活某历史版本。 */
    @Operation(summary = "激活历史版本")
    @PostMapping("/activate")
    public Result<Boolean> activateOutline(@RequestBody @Valid OutlineActivateRequest request) {
        return Result.success(outlineService.activateOutline(request));
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
