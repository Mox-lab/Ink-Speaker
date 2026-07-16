package ink.realm.novel.controller;

import ink.realm.common.result.Result;
import ink.realm.novel.domain.dto.NovelCreateRequest;
import ink.realm.novel.domain.dto.NovelUpdateRequest;
import ink.realm.novel.domain.vo.ContinuationSuggestionVo;
import ink.realm.novel.domain.vo.NovelExportPayload;
import ink.realm.novel.service.ContinuationService;
import ink.realm.novel.service.NovelService;
import ink.realm.novel.domain.vo.NovelOverviewVo;
import ink.realm.novel.domain.vo.NovelVo;
import ink.realm.novel.domain.vo.SaveResultVo;
import ink.realm.novel.domain.vo.SharedNovelBrowseVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
 *
 * <p>第 6 阶段(以小说为主体):新增 CRUD 与概览接口,支撑前端"我的小说列表 →
 * 进入某本小说 → 总览/续写/修改"信息架构。</p>
 * <ul>
 *   <li>{@code POST /api/data/novel} — 创建新小说</li>
 *   <li>{@code GET /api/data/novel/{id}} — 取单本小说基础信息</li>
 *   <li>{@code PUT /api/data/novel/{id}} — 更新小说基础信息</li>
 *   <li>{@code DELETE /api/data/novel/{id}} — 删除小说(含级联子表)</li>
 *   <li>{@code GET /api/data/novel/{id}/overview} — 取小说概览(基础信息 + 各子模块统计)</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/data/novel")
@RequiredArgsConstructor
@Tag(name = "Novel", description = "小说主表接口")
public class NovelController {

    private final NovelService novelService;
    private final ContinuationService continuationService;

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

    /**
     * 取公共参考池中某本小说的只读详情(BASE-09)。
     * <p>聚合脱敏基础信息 + 全部章节摘要 + 大纲版本 + 人物 + 设定,供前端浏览页一次性渲染。
     * 仅 {@code sharedForReference=true} 的小说可被获取。</p>
     */
    @Operation(summary = "取共享小说只读详情",
            description = "聚合章节/大纲/人物/设定,仅 sharedForReference=true 可访问")
    @GetMapping("/shared/{id}")
    public Result<SharedNovelBrowseVo> getSharedBrowse(@PathVariable Long id) {
        return Result.success(novelService.getSharedNovelBrowse(id));
    }

    /** 创建新小说。 */
    @Operation(summary = "创建新小说", description = "ownerId 强制取当前用户,不允许调用方传入")
    @PostMapping
    public Result<SaveResultVo> createNovel(@Valid @RequestBody NovelCreateRequest request) {
        return Result.success(novelService.createNovel(request));
    }

    /** 取单本小说基础信息(校验所有权)。 */
    @Operation(summary = "取小说详情", description = "不属于当前用户的小说返回 404")
    @GetMapping("/{id}")
    public Result<NovelVo> getNovel(@PathVariable Long id) {
        return Result.success(novelService.getNovel(id));
    }

    /** 更新小说基础信息(校验所有权)。 */
    @Operation(summary = "更新小说", description = "仅可改 title/author/description/sharedForReference")
    @PutMapping("/{id}")
    public Result<Void> updateNovel(@PathVariable Long id,
                                    @Valid @RequestBody NovelUpdateRequest request) {
        novelService.updateNovel(id, request);
        return Result.success();
    }

    /** 删除小说(级联删除章节/大纲/人物/设定/时间线/审查问题)。 */
    @Operation(summary = "删除小说", description = "整个级联在单事务中完成,任一子表失败则全部回滚")
    @DeleteMapping("/{id}")
    public Result<Void> deleteNovel(@PathVariable Long id) {
        novelService.deleteNovel(id);
        return Result.success();
    }

    /** 取小说概览(基础信息 + 各子模块统计 + 最近章节/大纲列表)。 */
    @Operation(summary = "取小说概览", description = "进入小说后第一屏直接渲染,避免多次请求")
    @GetMapping("/{id}/overview")
    public Result<NovelOverviewVo> getNovelOverview(@PathVariable Long id) {
        return Result.success(novelService.getNovelOverview(id));
    }

    /**
     * AI 续写建议(BASE-12)。
     * <p>基于已有章节 + 激活大纲 + 人物档案,预测下一章走向,返回结构化建议。</p>
     * <p>同步阻塞调用 LLM,前端按需触发(点击"AI 续写建议"按钮)。</p>
     */
    @Operation(summary = "AI 续写建议",
            description = "基于最近章节、激活大纲与人物档案预测下一章走向")
    @GetMapping("/{id}/continuation")
    public Result<ContinuationSuggestionVo> getContinuationSuggestion(@PathVariable Long id) {
        return Result.success(continuationService.suggestNextChapter(id));
    }

    /**
     * 导出小说(BASE-10)。
     * <p>聚合小说基础信息 + 大纲 + 人物 + 设定 + 全部章节,按 {@code format}
     * 拼装为下载文件返回。仅 owner 可导出。</p>
     * <p>格式:md / txt / json,默认 md。</p>
     * <p>响应为二进制附件,Content-Disposition 同时设置 ASCII filename 兜底与
     * RFC 5987 filename* UTF-8 编码,兼容各浏览器对中文文件名的处理。</p>
     */
    @Operation(summary = "导出小说",
            description = "聚合小说+大纲+人物+设定+章节,支持 md / txt / json 三种格式")
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportNovel(@PathVariable Long id,
                                              @RequestParam(required = false) String format) {
        NovelExportPayload payload = novelService.exportNovel(id, format);
        String asciiName = payload.filename().replaceAll("[^\\x20-\\x7E]", "_");
        String encodedName = URLEncoder.encode(payload.filename(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        String disposition = "attachment; filename=\"" + asciiName
                + "\"; filename*=UTF-8''" + encodedName;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(payload.contentType()));
        headers.add(HttpHeaders.CONTENT_DISPOSITION, disposition);
        headers.setContentLength(payload.content().length);
        return new ResponseEntity<>(payload.content(), headers, org.springframework.http.HttpStatus.OK);
    }
}
