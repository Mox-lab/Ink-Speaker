package ink.realm.novel.controller;

import ink.realm.common.context.NovelContext;
import ink.realm.common.result.Result;
import ink.realm.novel.domain.dto.SettingSaveRequest;
import ink.realm.novel.service.SettingService;
import ink.realm.novel.domain.vo.SaveResultVo;
import ink.realm.novel.domain.vo.WorldSettingVo;
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
    @GetMapping
    public Result<List<WorldSettingVo>> listSettings() {
        // novelId 由请求头 X-Novel-Id 经 NovelContextFilter 注入 NovelContext,
        // 与前端"第6阶段以小说为主体"的设计一致(业务层不再依赖路径变量)。
        return Result.success(settingService.listSettings(NovelContext.getNovelId()));
    }

    /** 保存单条设定(覆盖式)。 */
    @Operation(summary = "保存世界观设定")
    @PostMapping("/save")
    public Result<SaveResultVo> saveSetting(@RequestBody @Valid SettingSaveRequest request) {
        // 前端不传 novelId(走 X-Novel-Id 头),此处用 NovelContext 兜底,避免落到 DEFAULT_NOVEL_ID
        SettingSaveRequest req = request.novelId() != null ? request
                : new SettingSaveRequest(NovelContext.getNovelId(),
                request.keyword(), request.category(), request.description());
        return Result.success(settingService.saveSetting(req));
    }

    /** 删除某设定。 */
    @Operation(summary = "删除世界观设定")
    @DeleteMapping("/{id}")
    public Result<Void> deleteSetting(
            @Parameter(description = "设定 ID") @PathVariable Long id) {
        settingService.deleteSetting(id);
        return Result.success();
    }

    /** 按关键词模糊匹配设定(UX-06 写作侧边栏设定 RAG 检索)。 */
    @Operation(summary = "按关键词搜索设定")
    @GetMapping("/search")
    public Result<List<WorldSettingVo>> searchByKeyword(
            @Parameter(description = "关键词片段,空串返回全部") @RequestParam(required = false) String keyword) {
        return Result.success(settingService.searchByKeyword(NovelContext.getNovelId(), keyword));
    }
}
