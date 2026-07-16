package ink.realm.novel.controller;

import ink.realm.common.context.NovelContext;
import ink.realm.common.result.Result;
import ink.realm.novel.domain.dto.ChapterSaveRequest;
import ink.realm.novel.service.ChapterService;
import ink.realm.novel.domain.vo.ChapterDetailVo;
import ink.realm.novel.domain.vo.ChapterSummaryVo;
import ink.realm.novel.domain.vo.SaveResultVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 章节正文 CRUD 接口。
 * <p>统一基址 /api/data/chapter。同 novelId+chapterNo 覆盖保存。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/data/chapter")
@RequiredArgsConstructor
@Tag(name = "Chapter", description = "章节正文 CRUD 接口")
public class ChapterController {

    private final ChapterService chapterService;

    /** 列出某小说的全部章节(只返回摘要,不含正文)。 */
    @Operation(summary = "列出某小说的全部章节")
    @GetMapping
    public Result<List<ChapterSummaryVo>> listChapters() {
        // novelId 由 X-Novel-Id 头经 NovelContext 注入,详见 SettingController 说明
        return Result.success(chapterService.listChapters(NovelContext.getNovelId()));
    }

    /** 获取某章正文全文。 */
    @Operation(summary = "获取章节详情")
    @GetMapping("/detail/{id}")
    public Result<ChapterDetailVo> getChapter(
            @Parameter(description = "章节 ID") @PathVariable Long id) {
        return Result.success(chapterService.getChapter(id));
    }

    /** 保存章节(若已存在同 novelId+chapterNo 则覆盖)。 */
    @Operation(summary = "保存章节")
    @PostMapping("/save")
    public Result<SaveResultVo> saveChapter(@RequestBody @Valid ChapterSaveRequest request) {
        ChapterSaveRequest req = request.novelId() != null ? request
                : new ChapterSaveRequest(NovelContext.getNovelId(), request.chapterNo(),
                request.title(), request.content(), request.sessionId(),
                request.outlineId(), request.clientUpdatedAt());
        return Result.success(chapterService.saveChapter(req));
    }

    /** 删除某章。 */
    @Operation(summary = "删除章节")
    @DeleteMapping("/{id}")
    public Result<Void> deleteChapter(
            @Parameter(description = "章节 ID") @PathVariable Long id) {
        chapterService.deleteChapter(id);
        return Result.success();
    }
}
