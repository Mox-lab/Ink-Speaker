package com.ink.speaker.novel.controller;

import com.ink.speaker.common.Result;
import com.ink.speaker.novel.domain.dto.SettingSaveRequest;
import com.ink.speaker.novel.service.SettingService;
import com.ink.speaker.novel.domain.vo.SaveResultVo;
import com.ink.speaker.novel.domain.vo.WorldSettingVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 世界观设定 CRUD 接口。
 * <p>统一基址 /api/data/setting。按 novelId+keyword 覆盖保存。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/data/setting")
@RequiredArgsConstructor
@Tag(name = "Setting", description = "世界观设定 CRUD 接口")
public class SettingController {

    private final SettingService settingService;

    /** 列出某小说全部设定。 */
    @Operation(summary = "列出世界观设定")
    @GetMapping("/{novelId}")
    public Result<List<WorldSettingVo>> listSettings(
            @Parameter(description = "小说 ID") @PathVariable Long novelId) {
        return Result.success(settingService.listSettings(novelId));
    }

    /** 保存单条设定(覆盖式)。 */
    @Operation(summary = "保存世界观设定")
    @PostMapping("/save")
    public Result<SaveResultVo> saveSetting(@RequestBody @Valid SettingSaveRequest request) {
        return Result.success(settingService.saveSetting(request));
    }

    /** 删除某设定。 */
    @Operation(summary = "删除世界观设定")
    @DeleteMapping("/{id}")
    public Result<Void> deleteSetting(
            @Parameter(description = "设定 ID") @PathVariable Long id) {
        settingService.deleteSetting(id);
        return Result.success();
    }
}
