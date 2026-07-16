package ink.realm.novel.controller;

import ink.realm.common.context.NovelContext;
import ink.realm.common.result.Result;
import ink.realm.novel.domain.dto.TrackRequest;
import ink.realm.novel.service.AgentLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 漏斗埋点上报接口(UX-11)。
 * <p>统一基址 /api/track。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/track")
@RequiredArgsConstructor
@Tag(name = "Track", description = "前端埋点上报接口")
public class TrackController {

    private final AgentLogService agentLogService;

    /**
     * 上报一条漏斗事件。
     * <p>失败仅记录日志,始终返回 success,避免影响前端主流程。</p>
     */
    @Operation(summary = "上报漏斗事件")
    @PostMapping
    public Result<Void> track(@Valid @RequestBody TrackRequest request) {
        // userId 从 JWT 解析后的 ThreadLocal 取;未登录场景为 null,允许上报匿名事件
        Long userId = NovelContext.getUserId();
        agentLogService.track(request.eventType(), request.props(), userId, request.novelId());
        return Result.success(null);
    }
}
