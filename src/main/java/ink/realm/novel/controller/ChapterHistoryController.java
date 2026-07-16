package ink.realm.novel.controller;

import ink.realm.common.result.Result;
import ink.realm.novel.domain.vo.ChapterHistoryVo;
import ink.realm.novel.service.ChapterHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 章节历史快照接口(BASE-07)。
 * <p>统一基址 /api/data/chapter/history。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/data/chapter/history")
@RequiredArgsConstructor
@Tag(name = "ChapterHistory", description = "章节历史版本快照接口")
public class ChapterHistoryController {

    private final ChapterHistoryService chapterHistoryService;

    /** 列出某章节的全部历史快照(按时间倒序)。 */
    @Operation(summary = "列出章节历史快照")
    @GetMapping
    public Result<List<ChapterHistoryVo>> listByChapter(
            @Parameter(description = "章节 ID") @RequestParam Long chapterId) {
        return Result.success(chapterHistoryService.listByChapter(chapterId));
    }

    /** 取某条历史快照详情(含 content 全文)。 */
    @Operation(summary = "获取历史快照详情")
    @GetMapping("/{historyId}")
    public Result<ChapterHistoryVo> getHistory(
            @Parameter(description = "历史 ID") @PathVariable Long historyId) {
        return Result.success(chapterHistoryService.getHistory(historyId));
    }
}
