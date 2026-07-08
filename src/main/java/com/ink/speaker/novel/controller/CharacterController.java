package com.ink.speaker.novel.controller;

import com.ink.speaker.common.Result;
import com.ink.speaker.novel.domain.dto.CharacterBatchSaveRequest;
import com.ink.speaker.novel.service.CharacterService;
import com.ink.speaker.novel.domain.vo.CharacterBatchSaveResultVo;
import com.ink.speaker.novel.domain.vo.CharacterVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 人物档案 CRUD 接口。
 * <p>统一基址 /api/data/character。批量保存(upsert by novelId+name)。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/data/character")
@RequiredArgsConstructor
@Tag(name = "Character", description = "人物档案 CRUD 接口")
public class CharacterController {

    private final CharacterService characterService;

    /** 列出某小说全部人物(含 relationships)。 */
    @Operation(summary = "列出人物")
    @GetMapping("/{novelId}")
    public Result<List<CharacterVo>> listCharacters(
            @Parameter(description = "小说 ID") @PathVariable Long novelId) {
        return Result.success(characterService.listCharacters(novelId));
    }

    /** 批量保存人物(覆盖式)。 */
    @Operation(summary = "批量保存人物")
    @PostMapping("/save-batch")
    public Result<CharacterBatchSaveResultVo> saveCharactersBatch(
            @RequestBody @Valid CharacterBatchSaveRequest request) {
        return Result.success(characterService.saveBatch(request));
    }

    /** 删除某人物。 */
    @Operation(summary = "删除人物")
    @DeleteMapping("/{id}")
    public Result<Void> deleteCharacter(
            @Parameter(description = "人物 ID") @PathVariable Long id) {
        characterService.deleteCharacter(id);
        return Result.success();
    }
}
