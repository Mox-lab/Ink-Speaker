package com.ink.speaker.novel.controller;

import com.ink.speaker.common.Result;
import com.ink.speaker.novel.service.NovelService;
import com.ink.speaker.novel.domain.vo.NovelVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小说主表 CRUD 接口。
 * <p>统一基址 /api/data/novel。</p>
 *
 * <p>第 5 阶段(R5 用户隔离):</p>
 * <ul>
 *   <li>{@code GET /api/data/novel} — 仅返回当前用户拥有的小说</li>
 *   <li>{@code GET /api/data/novel/shared} — 公共参考池(脱敏),任何登录用户可见</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/data/novel")
@RequiredArgsConstructor
@Tag(name = "Novel", description = "小说主表接口")
public class NovelController {

    private final NovelService novelService;

    /** 列出当前用户的小说(R5 用户隔离)。 */
    @Operation(summary = "列出我的小说", description = "仅返回当前用户拥有的小说")
    @GetMapping
    public Result<List<NovelVo>> listNovels() {
        return Result.success(novelService.listNovels());
    }

    /** 列出公开到公共参考池的小说(R5 跨小说参考)。 */
    @Operation(summary = "列出公共参考池", description = "返回所有用户公开的脱敏小说列表")
    @GetMapping("/shared")
    public Result<List<NovelVo>> listShared() {
        return Result.success(novelService.listSharedForReference());
    }
}
