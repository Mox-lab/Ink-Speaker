package ink.realm.novel.controller;

import ink.realm.common.context.NovelContext;
import ink.realm.common.result.Result;
import ink.realm.novel.domain.dto.CharacterBatchSaveRequest;
import ink.realm.novel.service.CharacterService;
import ink.realm.novel.domain.vo.CharacterBatchSaveResultVo;
import ink.realm.novel.domain.vo.CharacterVo;
import ink.realm.novel.domain.vo.ChapterSummaryVo;
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
    @GetMapping
    public Result<List<CharacterVo>> listCharacters() {
        // novelId 由 X-Novel-Id 头经 NovelContext 注入,详见 SettingController 说明
        return Result.success(characterService.listCharacters(NovelContext.getNovelId()));
    }

    /** 批量保存人物(覆盖式)。 */
    @Operation(summary = "批量保存人物")
    @PostMapping("/save-batch")
    public Result<CharacterBatchSaveResultVo> saveCharactersBatch(
            @RequestBody @Valid CharacterBatchSaveRequest request) {
        CharacterBatchSaveRequest req = request.novelId() != null ? request
                : new CharacterBatchSaveRequest(NovelContext.getNovelId(), request.characters());
        return Result.success(characterService.saveBatch(req));
    }

    /** 删除某人物。 */
    @Operation(summary = "删除人物")
    @DeleteMapping("/{id}")
    public Result<Void> deleteCharacter(
            @Parameter(description = "人物 ID") @PathVariable Long id) {
        characterService.deleteCharacter(id);
        return Result.success();
    }

    /** 按名字模糊匹配人物(UX-06 写作侧边栏 @人物搜索)。 */
    @Operation(summary = "按名字搜索人物")
    @GetMapping("/by-name")
    public Result<List<CharacterVo>> searchByName(
            @Parameter(description = "姓名片段,空串返回全部") @RequestParam(required = false) String name) {
        return Result.success(characterService.searchByName(NovelContext.getNovelId(), name));
    }

    /** 列出某人物出现的章节(UX-06 人物卡片点击查看出现位置)。 */
    @Operation(summary = "人物出现章节列表")
    @GetMapping("/appears")
    public Result<List<ChapterSummaryVo>> listAppearedChapters(
            @Parameter(description = "人物 ID") @RequestParam Long characterId) {
        return Result.success(characterService.listAppearedChapters(NovelContext.getNovelId(), characterId));
    }
}
