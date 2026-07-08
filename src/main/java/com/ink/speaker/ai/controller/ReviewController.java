package com.ink.speaker.ai.controller;

import com.ink.speaker.common.Result;
import com.ink.speaker.ai.core.director.DirectorAgent;
import com.ink.speaker.ai.domain.director.dto.ReviewStatusUpdateRequest;
import com.ink.speaker.ai.domain.director.dto.ReviewTriggerRequest;
import com.ink.speaker.ai.domain.director.vo.ReviewTriggerVo;
import com.ink.speaker.novel.domain.vo.ReviewIssueVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 章节审查问题接口(P1 多 Agent 协作产物)。
 * <p>统一基址 /api/review。供前端"审查问题侧栏"使用。</p>
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
@Tag(name = "Review", description = "章节审查问题接口")
public class ReviewController {

    private final DirectorAgent directorAgent;

    /** 列出某章的全部审查问题(按严重度降序)。 */
    @Operation(summary = "列出某章审查问题")
    @GetMapping("/chapter/{chapterNo}")
    public Result<List<ReviewIssueVo>> listByChapter(
            @Parameter(description = "章节序号") @PathVariable int chapterNo) {
        return Result.success(directorAgent.listIssues(chapterNo));
    }

    /** 列出当前小说全部未解决的审查问题(供侧栏展示)。 */
    @Operation(summary = "列出未解决审查问题")
    @GetMapping("/open")
    public Result<List<ReviewIssueVo>> listOpen() {
        return Result.success(directorAgent.listOpenIssues());
    }

    /** 手动触发某章审查(用于调试或重新审查)。 */
    @Operation(summary = "手动触发章节审查")
    @PostMapping("/chapter/{chapterNo}")
    public Result<ReviewTriggerVo> triggerReview(
            @Parameter(description = "章节序号") @PathVariable int chapterNo,
            @RequestBody @Valid ReviewTriggerRequest body) {
        directorAgent.reviewChapter(body.content(), chapterNo);
        return Result.success(ReviewTriggerVo.builder()
                .success(true)
                .message("审查任务已异步触发")
                .build());
    }

    /** 更新审查问题状态(open / resolved / ignored)。 */
    @Operation(summary = "更新审查问题状态")
    @PatchMapping("/{id}")
    public Result<ReviewIssueVo> updateStatus(
            @Parameter(description = "问题 ID") @PathVariable Long id,
            @RequestBody @Valid ReviewStatusUpdateRequest body) {
        return Result.success(directorAgent.updateStatus(id, body.status()));
    }
}

