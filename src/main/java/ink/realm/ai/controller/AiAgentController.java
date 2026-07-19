package ink.realm.ai.controller;

import ink.realm.ai.domain.agent.ChapterRequest;
import ink.realm.ai.domain.agent.ChapterResponse;
import ink.realm.ai.domain.agent.CharacterExtractRequest;
import ink.realm.ai.domain.agent.CharacterProfile;
import ink.realm.ai.domain.agent.ChatReply;
import ink.realm.ai.domain.agent.ChatRequest;
import ink.realm.ai.domain.agent.ConceptRequest;
import ink.realm.ai.domain.agent.ConceptResponse;
import ink.realm.ai.domain.agent.LoreImportRequest;
import ink.realm.ai.domain.agent.LoreImportResponse;
import ink.realm.ai.domain.agent.LoreRequest;
import ink.realm.ai.domain.agent.LoreResponse;
import ink.realm.ai.domain.agent.LoreSearchHit;
import ink.realm.ai.domain.agent.LoreSearchRequest;
import ink.realm.ai.domain.agent.OutlineRequest;
import ink.realm.ai.domain.agent.OutlineResponse;
import ink.realm.ai.domain.agent.PolishRequest;
import ink.realm.ai.domain.agent.PolishResponse;
import ink.realm.ai.domain.agent.SettingRequest;
import ink.realm.ai.domain.agent.SettingResponse;
import ink.realm.ai.domain.agent.SkillInfo;
import ink.realm.ai.domain.agent.SkillPreviewRequest;
import ink.realm.ai.domain.agent.WritingRequest;
import ink.realm.ai.domain.agent.WritingResponse;
import ink.realm.ai.agent.ChapterAgentFactory;
import ink.realm.ai.agent.CharacterExtractionAgent;
import ink.realm.ai.service.KnowledgeBaseService;
import ink.realm.ai.agent.LoreAgent;
import ink.realm.ai.agent.WritingAssistantAgentFactory;
import ink.realm.ai.agent.ChapterAgent;
import ink.realm.ai.cache.LlmCacheService;
import ink.realm.ai.core.skill.Skill;
import ink.realm.ai.core.skill.SkillRegistry;
import ink.realm.common.exception.BusinessException;
import ink.realm.common.result.Result;
import ink.realm.common.result.ResultCode;
import ink.realm.util.ArgsUtil;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;

/**
 * 小说创作 Agent REST 接口。
 * <p>按真实小说家创作流程组织:构思 → 设定 → 大纲 → 章节 → 润色。
 * 辅以通用助手、设定问答、人物抽取等工具接口。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Agent", description = "小说创作 Agent 主流程接口")
public class AiAgentController {

    private static final long SSE_TIMEOUT_MS = 120_000L;
    private static final int FALLBACK_CHAPTERS = 20;

    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;
    private final WritingAssistantAgentFactory writingAssistantAgentFactory;
    private final ChapterAgentFactory chapterAgentFactory;
    private final CharacterExtractionAgent characterExtractionAgent;
    private final LoreAgent loreAgent;
    private final KnowledgeBaseService knowledgeBaseService;
    private final SkillRegistry skillRegistry;
    private final ExecutorService sseStreamExecutor;  // SSE 流式任务专用线程池(Spring 容器管理生命周期,SonarQube S2095)
    private final LlmCacheService llmCacheService;    // L1 LLM 响应缓存包装层

    // ==================== 主流程:5 阶段创作 ====================

    /**
     * 阶段 1 / 5 — 构思。
     * <p>从一句话灵感扩展为题材蓝图(简介、冲突、卖点、读者、基调、篇幅)。</p>
     */
    @Operation(summary = "阶段 1/5 构思", description = "从一句话灵感扩展为题材蓝图")
    @PostMapping("/concept")
    public Result<ConceptResponse> concept(@RequestBody @Valid ConceptRequest request) {
        String inspiration = request.inspiration();
        String genre = request.genre() != null ? request.genre() : "";
        log.info("[/concept] genre={}, inspiration={}", genre, inspiration);
        try {
            String blueprint = llmCacheService.expandConcept(inspiration, genre);
            return Result.success(ConceptResponse.builder()
                    .inspiration(inspiration)
                    .blueprint(blueprint)
                    .build());
        } catch (Exception e) {
            log.error("[/concept] fail", e);
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "构思失败:" + ArgsUtil.reasonOf(e));
        }
    }

    /**
     * 阶段 2 / 5 — 设定。
     * <p>基于题材蓝图生成世界观 + 主要人物档案。</p>
     */
    @Operation(summary = "阶段 2/5 设定", description = "基于蓝图生成世界观与人物档案")
    @PostMapping("/setting")
    public Result<SettingResponse> setting(@RequestBody @Valid SettingRequest request) {
        String blueprint = request.blueprint();
        String tone = request.tone() != null ? request.tone() : "";
        log.info("[/setting] tone={}", tone);
        try {
            String settingText = llmCacheService.buildSetting(blueprint, tone);
            return Result.success(SettingResponse.builder()
                    .blueprint(blueprint)
                    .setting(settingText)
                    .build());
        } catch (Exception e) {
            log.error("[/setting] fail", e);
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "设定生成失败:" + ArgsUtil.reasonOf(e));
        }
    }

    /**
     * 阶段 3 / 5 — 大纲(全量快捷生成)。
     * <p>两步式生成:先 {@code planVolumes} 规划卷结构(可由前端编辑后回传 volumePlan),
     * 再逐卷 {@code expandVolume} 展开为逐章细纲,每卷生成后 {@code selfCheck} 自检,
     * 偏离主题 / 与前文矛盾 / 编号不连续时带反馈重试一次。
     * <p>新流程推荐:先调 {@code /outline/plan} 规划,再逐卷调 {@code /outline/volume} 展开,
     * 以获得更可控、可中断的逐卷体验。</p>
     */
    @Operation(summary = "阶段 3/5 大纲", description = "卷规划 + 分卷展开 + 自检,卷数章数由模型决定")
    @PostMapping("/outline")
    public Result<OutlineResponse> outline(@RequestBody @Valid OutlineRequest request) {
        String blueprint = resolveBlueprint(request);
        String setting = request.setting() != null ? request.setting() : "";
        String volumePlanOverride = request.volumePlan();
        String theme = (request.theme() != null && !request.theme().isBlank())
                ? request.theme() : blueprint;
        log.info("[/outline] customPlan={}",
                volumePlanOverride != null && !volumePlanOverride.isBlank());

        try {
            String plan = (volumePlanOverride != null && !volumePlanOverride.isBlank())
                    ? volumePlanOverride
                    : llmCacheService.planVolumes(blueprint, setting);
            List<VolumeSpec> volumes = buildVolumes(plan);
            String outlineText = expandVolumes(blueprint, setting, theme, plan, volumes);
            int totalChapters = volumes.stream().mapToInt(VolumeSpec::chapterCount).sum();

            if (outlineText.isBlank()) {
                log.warn("[/outline] empty result, likely finish_reason=length (reasoning burned tokens)");
                return Result.success(OutlineResponse.builder()
                        .error("大纲生成失败:模型输出为空(可能因思维链烧光 token,请调高 max-tokens 或减少章节数后重试)")
                        .build());
            }
            return Result.success(OutlineResponse.builder()
                    .chapters(totalChapters)
                    .outline(outlineText)
                    .volumePlan(plan)
                    .volumes(volumes.isEmpty() ? null : volumes.size())
                    .build());
        } catch (Exception e) {
            log.error("[/outline] fail", e);
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "大纲生成失败:" + ArgsUtil.reasonOf(e));
        }
    }

    /**
     * 阶段 3 / 5 — 仅规划卷结构(供前端编辑后回传 volumePlan 再生成大纲)。
     */
    @Operation(summary = "大纲卷规划", description = "只生成卷结构计划,可编辑后回传 /api/outline 的 volumePlan 字段")
    @PostMapping("/outline/plan")
    public Result<OutlineResponse> outlinePlan(@RequestBody @Valid OutlineRequest request) {
        String blueprint = resolveBlueprint(request);
        String setting = request.setting() != null ? request.setting() : "";
        log.info("[/outline/plan]");
        try {
            String plan = llmCacheService.planVolumes(blueprint, setting);
            List<VolumeSpec> volumes = buildVolumes(plan);
            int totalChapters = volumes.stream().mapToInt(VolumeSpec::chapterCount).sum();
            return Result.success(OutlineResponse.builder()
                    .chapters(totalChapters)
                    .outline(plan)
                    .volumePlan(plan)
                    .volumes(volumes.isEmpty() ? null : volumes.size())
                    .build());
        } catch (Exception e) {
            log.error("[/outline/plan] fail", e);
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "卷规划失败:" + ArgsUtil.reasonOf(e));
        }
    }

    /**
     * 阶段 3 / 5 — 展开单卷细纲。
     * <p>前端完成卷规划后,针对某一卷调用本接口,基于整书卷规划展开该卷的逐章细纲。
     * 可逐卷调用,也可在前端循环调用实现"展开全部"。</p>
     */
    @Operation(summary = "展开单卷细纲", description = "基于卷规划展开指定卷的逐章细纲")
    @PostMapping("/outline/volume")
    public Result<OutlineResponse> outlineVolume(@RequestBody @Valid OutlineRequest request) {
        String blueprint = resolveBlueprint(request);
        String setting = request.setting() != null ? request.setting() : "";
        String volumePlan = request.volumePlan();
        int volumeIndex = request.volumeIndex() != null ? request.volumeIndex() : 1;
        String theme = (request.theme() != null && !request.theme().isBlank())
                ? request.theme() : blueprint;
        if (volumePlan == null || volumePlan.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请先完成卷规划再展开本卷细纲");
        }
        log.info("[/outline/volume] index={}", volumeIndex);
        try {
            List<VolumeSpec> volumes = buildVolumes(volumePlan);
            VolumeSpec target = volumes.stream()
                    .filter(v -> v.index() == volumeIndex)
                    .findFirst()
                    .orElse(null);
            if (target == null) {
                return Result.success(OutlineResponse.builder()
                        .error("未找到第 " + volumeIndex + " 卷,请检查卷规划")
                        .build());
            }
            String seg = llmCacheService.expandVolume(blueprint, setting, volumePlan, "",
                    target.name(), target.arc(), target.start(), target.end());
            // 自检:偏离主题 / 与前文矛盾 / 编号不连续 → 带反馈重试一次
            String context = "【主题】" + theme + "\n【整书规划】\n" + volumePlan;
            String check = llmCacheService.selfCheck(theme, seg, context);
            if (check != null && check.contains("需修正")) {
                log.info("[/outline/volume] 第 {} 卷自检未通过,带反馈重试", volumeIndex);
                String retryBlueprint = blueprint + "\n\n【自检反馈,必须修正以下问题后重新输出本卷细纲】\n" + check;
                String seg2 = llmCacheService.expandVolume(retryBlueprint, setting, volumePlan, "",
                        target.name(), target.arc(), target.start(), target.end());
                if (seg2 != null && !seg2.isBlank()) {
                    seg = seg2;
                }
            }
            if (seg == null || seg.isBlank()) {
                return Result.success(OutlineResponse.builder()
                        .error("第 " + volumeIndex + " 卷展开失败,请重试")
                        .build());
            }
            return Result.success(OutlineResponse.builder()
                    .chapters(target.end() - target.start() + 1)
                    .outline(seg)
                    .volumePlan(volumePlan)
                    .volumes(volumeIndex)
                    .build());
        } catch (Exception e) {
            log.error("[/outline/volume] fail index={}", volumeIndex, e);
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "展开卷细纲失败:" + ArgsUtil.reasonOf(e));
        }
    }

    /**
     * 解析卷规划文本为结构化卷列表,并按规划中的"章数"分配起止章号(全局连续)。
     * <p>各卷章数直接取自规划文本,全书章数 = 各卷章数之和(不再由外部章节数约束)。
     * 若 LLM 未遵循格式,降级为单卷承载默认章节数。</p>
     */
    private List<VolumeSpec> buildVolumes(String plan) {
        List<VolumeSpec> list = new ArrayList<>();
        Pattern volRe = Pattern.compile("##\\s*第\\s*(\\d+)\\s*卷\\s*[·:：]?\\s*(.+)");
        Pattern arcRe = Pattern.compile("卷主线[:：]\\s*(.+)");
        Pattern chRe = Pattern.compile("章数[:：]\\s*(\\d+)");
        Integer curIdx = null;
        String curName = null;
        String curArc = null;
        Integer curCh = null;
        for (String line : plan.split("\n")) {
            String t = line.trim();
            Matcher vm = volRe.matcher(t);
            if (vm.find()) {
                if (curIdx != null) {
                    list.add(new VolumeSpec(curIdx, curName, curArc, curCh != null ? curCh : 0));
                }
                curIdx = Integer.parseInt(vm.group(1));
                curName = vm.group(2).trim();
                curArc = null;
                curCh = null;
                continue;
            }
            if (curIdx == null) {
                continue;
            }
            Matcher am = arcRe.matcher(t);
            if (am.find()) {
                curArc = am.group(1).trim();
                continue;
            }
            Matcher cm = chRe.matcher(t);
            if (cm.find()) {
                curCh = Integer.parseInt(cm.group(1));
            }
        }
        if (curIdx != null) {
            list.add(new VolumeSpec(curIdx, curName, curArc, curCh != null ? curCh : 0));
        }

        if (list.isEmpty()) {
            // 降级:单卷承载默认章节数
            list.add(new VolumeSpec(1, "正文", "", FALLBACK_CHAPTERS));
        }
        // 按卷号排序,规整起止章号(全局连续,由各卷"章数"之和决定全书总量)
        list.sort(Comparator.comparingInt(VolumeSpec::index));
        int cursor = 1;
        List<VolumeSpec> resolved = new ArrayList<>();
        for (VolumeSpec v : list) {
            int count = Math.max(1, v.chapterCount());
            int start = cursor;
            int end = cursor + count - 1;
            resolved.add(new VolumeSpec(v.index(), v.name(), v.arc(), count, start, end));
            cursor = end + 1;
        }
        return resolved;
    }

    /**
     * 解析 outline 请求的 blueprint:theme 兜底。
     */
    private String resolveBlueprint(OutlineRequest request) {
        String blueprint = (request.blueprint() == null || request.blueprint().isBlank())
                ? request.theme() : request.blueprint();
        if (blueprint == null || blueprint.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "blueprint 与 theme 至少传一个");
        }
        return blueprint;
    }

    /**
     * 逐卷展开为逐章细纲,携带前情 + 自检机制。
     * <p>每卷展开后做主题 / 连贯 / 编号自检,命中"需修正"时带反馈重试一次。
     * 已完成的各卷摘要(prevVolumes)持续累积,作为下一卷生成的"前情",保证跨卷连贯。</p>
     */
    private String expandVolumes(String blueprint, String setting, String theme,
                                 String plan, List<VolumeSpec> volumes) {
        StringBuilder sb = new StringBuilder();
        StringBuilder prevVolumes = new StringBuilder();
        for (VolumeSpec v : volumes) {
            String seg = llmCacheService.expandVolume(blueprint, setting, plan,
                    prevVolumes.toString(), v.name(), v.arc(), v.start(), v.end());
            // 自检:偏离主题 / 与前文矛盾 / 编号不连续 → 带反馈重试一次
            String context = "【主题】" + theme + "\n【整书规划】\n" + plan + "\n【前情摘要】\n" + prevVolumes;
            String check = llmCacheService.selfCheck(theme, seg, context);
            if (check != null && check.contains("需修正")) {
                log.info("[/outline] 第 {} 卷自检未通过,带反馈重试", v.index());
                String retryBlueprint = blueprint + "\n\n【自检反馈,必须修正以下问题后重新输出本卷细纲】\n" + check;
                String seg2 = llmCacheService.expandVolume(retryBlueprint, setting, plan,
                        prevVolumes + "\n\n自检问题:\n" + check, v.name(), v.arc(), v.start(), v.end());
                if (seg2 != null && !seg2.isBlank()) {
                    seg = seg2;
                }
            }
            if (seg == null || seg.isBlank()) {
                log.warn("[/outline] 第 {} 卷展开为空,跳过", v.index());
                prevVolumes.append("\n[第").append(v.index()).append("卷《").append(v.name()).append("》] (生成失败)");
                continue;
            }
            sb.append("## 第 ").append(v.index()).append(" 卷 · ").append(v.name()).append("\n");
            if (v.arc() != null && !v.arc().isBlank()) {
                sb.append("卷主线:").append(v.arc()).append("\n\n");
            }
            sb.append(seg).append("\n\n");
            // 累积本卷摘要,作为下一卷的"前情"
            prevVolumes.append(summarizeVolume(v, seg));
        }
        return sb.toString().trim();
    }

    /**
     * 把一卷生成的逐章细纲压缩为"前情摘要",用于下一卷生成的连贯性自检上下文。
     * <p>只保留章号/章名与"主线"一句,控制长度。</p>
     */
    private String summarizeVolume(VolumeSpec v, String seg) {
        StringBuilder b = new StringBuilder();
        b.append("\n[第").append(v.index()).append("卷《").append(v.name()).append("》] 主线:")
                .append(v.arc() == null ? "" : v.arc()).append("\n");
        Pattern chRe = Pattern.compile("#{1,4}\\s*第\\s*\\d+\\s*章\\s*(.*)$");
        Pattern mainRe = Pattern.compile("-\\s*主线[:：]\\s*(.+)");
        for (String line : seg.split("\n")) {
            String t = line.trim();
            Matcher cm = chRe.matcher(t);
            if (cm.find()) {
                b.append("第").append(t.replaceAll("#", "").trim()).append(" ");
                continue;
            }
            Matcher mm = mainRe.matcher(t);
            if (mm.find()) {
                b.append(mm.group(1).trim()).append("\n");
            }
        }
        return b.toString();
    }

    /** 卷结构(索引 / 卷名 / 主线 / 章数 / 全局起止章号)。 */
    private record VolumeSpec(int index, String name, String arc, int chapterCount, int start, int end) {
        VolumeSpec(int index, String name, String arc, int chapterCount) {
            this(index, name, arc, chapterCount, 0, 0);
        }
    }

    /**
     * 阶段 4 / 5 — 章节。
     * <p>按本章大纲生成正文,同一 sessionId 共享 Memory 保持连贯。</p>
     * <p>P1 新增:若请求含 {@code skillId} 则强制使用该 Skill;否则按 outline+setting
     * 自动匹配。命中 Skill 的 {@code promptSuffix} 会拼到 outline 前,引导 LLM 风格化写作。</p>
     */
    @Operation(summary = "阶段 4/5 章节", description = "按大纲生成章节正文,支持 Skill 风格切换")
    @PostMapping("/chapter")
    public Result<ChapterResponse> chapter(@RequestBody @Valid ChapterRequest request) {
        String sessionId = request.sessionId() != null ? request.sessionId() : "novel-001";
        String outline = request.outline();
        String setting = request.setting();
        int wordCount = request.wordCount() != null ? request.wordCount() : 2000;
        String forceSkillId = request.skillId();
        log.info("[/chapter] sessionId={}, wordCount={}, skillId={}", sessionId, wordCount, forceSkillId);
        try {
            // Skill 解析:强制 id 优先,否则按 outline+setting 自动匹配
            String skillContext = outline + "\n" + setting;
            Skill skill = skillRegistry.resolve(skillContext, forceSkillId);
            log.info("[/chapter] resolved skill id={}, name={}", skill.id(), skill.name());

            // 把 skill 的 promptSuffix 注入到 outline 前作为风格指令
            String enrichedOutline = new StringBuilder()
                    .append("【本章风格指引】\n").append(skill.promptSuffix())
                    .append("\n【本章大纲】\n").append(outline)
                    .toString();

            // 按 Skill.toolWhitelist 动态获取 Agent:无白名单时使用全部工具
            ChapterAgent chapterAgent = chapterAgentFactory.get(skill.toolWhitelist());
            String content = chapterAgent.write(sessionId, enrichedOutline, setting, wordCount);

            return Result.success(ChapterResponse.builder()
                    .sessionId(sessionId)
                    .content(content)
                    .skillId(skill.id())
                    .skillName(skill.name())
                    .build());
        } catch (Exception e) {
            log.error("[/chapter] fail sessionId={}", sessionId, e);
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "章节生成失败:" + ArgsUtil.reasonOf(e));
        }
    }

    /**
     * 阶段 5 / 5 — 润色。
     * <p>对章节草稿做节奏/对话/文笔/错别字维度的润色,不改变剧情。</p>
     */
    @Operation(summary = "阶段 5/5 润色", description = "对草稿做节奏/对话/文笔/错别字润色")
    @PostMapping("/polish")
    public Result<PolishResponse> polish(@RequestBody @Valid PolishRequest request) {
        String draft = request.draft();
        String focus = request.focus() != null ? request.focus() : "节奏,对话,文笔,错别字";
        String intensity = request.intensity() != null ? request.intensity() : "medium";
        log.info("[/polish] intensity={}, focus={}", intensity, focus);
        try {
            String polished = llmCacheService.polish(draft, focus, intensity);
            return Result.success(PolishResponse.builder()
                    .draft(draft)
                    .polished(polished)
                    .build());
        } catch (Exception e) {
            log.error("[/polish] fail", e);
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "润色失败:" + ArgsUtil.reasonOf(e));
        }
    }

    // ==================== 辅助接口 ====================

    /**
     * 通用对话(单轮,无记忆)。
     */
    @Operation(summary = "通用对话", description = "单轮对话,无记忆")
    @PostMapping("/chat")
    public Result<ChatReply> chat(@RequestBody @Valid ChatRequest request) {
        String message = request.message();
        log.info("[/chat] message={}", message);
        String reply = chatModel.chat(message);
        return Result.success(ChatReply.builder().reply(reply).build());
    }

    /**
     * 流式对话(SSE 打字机效果)。
     */
    @Operation(summary = "流式对话", description = "SSE 流式输出")
    @PostMapping(value = "/chat/stream", produces = "text/event-stream")
    public SseEmitter chatStream(@RequestBody @Valid ChatRequest request) {
        String message = request.message();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        // 使用容器托管的 ExecutorService:由 Spring 统一关闭,无需在方法内 shutdown,
        // 规避 SonarQube S2095(未在 finally 中关闭 ExecutorService)。
        sseStreamExecutor.execute(() -> streamingChatModel.chat(message, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                try {
                    emitter.send(SseEmitter.event().data(partialResponse));
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                emitter.complete();
            }

            @Override
            public void onError(Throwable error) {
                emitter.completeWithError(error);
            }
        }));
        return emitter;
    }

    /**
     * 写作助手(多轮记忆 + 工具调用),用于随时探讨剧情/卡文。
     * <p>P1 新增:自动按 message 匹配 Skill,把 promptSuffix 拼到用户消息前。</p>
     */
    @Operation(summary = "写作助手", description = "多轮记忆 + 工具调用,支持 Skill 风格")
    @PostMapping("/writing")
    public Result<WritingResponse> writing(@RequestBody @Valid WritingRequest request) {
        String userId = request.userId() != null ? request.userId() : "writer-001";
        String message = request.message();
        String forceSkillId = request.skillId();
        log.info("[/writing] userId={}, message={}", userId, message);

        Skill skill = skillRegistry.resolve(message, forceSkillId);
        String enriched = new StringBuilder()
                .append("【风格指引】\n").append(skill.promptSuffix())
                .append("\n【作者提问】\n").append(message)
                .toString();

        // 按 Skill.toolWhitelist 动态获取 Agent
        var agent = writingAssistantAgentFactory.get(skill.toolWhitelist());
        String reply = agent.chat(userId, enriched);

        return Result.success(WritingResponse.builder()
                .userId(userId)
                .reply(reply)
                .skillId(skill.id())
                .skillName(skill.name())
                .build());
    }

    /**
     * 人物抽取(结构化输出)。
     */
    @Operation(summary = "人物抽取", description = "从文本中抽取结构化人物档案")
    @PostMapping("/character")
    public Result<CharacterProfile> character(
            @RequestBody @Valid CharacterExtractRequest request) {
        String text = request.text();
        log.info("[/character] text length={}", text.length());
        return Result.success(characterExtractionAgent.extract(text));
    }

    /**
     * 设定问答(RAG)。
     */
    @Operation(summary = "设定问答", description = "RAG 检索知识库后回答")
    @PostMapping("/lore")
    public Result<LoreResponse> lore(@RequestBody @Valid LoreRequest request) {
        String sessionId = request.sessionId() != null ? request.sessionId() : "lore-001";
        String question = request.question();
        log.info("[/lore] question={}", question);
        String answer = loreAgent.ask(sessionId, question);
        return Result.success(LoreResponse.builder().answer(answer).build());
    }

    /**
     * 导入设定库(支持 text 或 dir)。
     */
    @Operation(summary = "导入设定库", description = "支持 text 或 dir 参数")
    @PostMapping("/lore/import")
    public Result<LoreImportResponse> importLore(@RequestBody @Valid LoreImportRequest request) {
        String dir = request.dir();
        String text = request.text();
        if (text != null && !text.isBlank()) {
            knowledgeBaseService.addText(text, "manual");
            return Result.success(LoreImportResponse.builder()
                    .success(true).added(1).build());
        }
        if (dir != null && !dir.isBlank()) {
            int count = knowledgeBaseService.importDocuments(dir);
            return Result.success(LoreImportResponse.builder()
                    .success(true).added(count).build());
        }
        throw new BusinessException(ResultCode.PARAM_INVALID, "请传入 dir 或 text 参数");
    }

    /**
     * 检索调试:查看设定库的检索结果。
     */
    @Operation(summary = "检索调试", description = "查看设定库的检索结果")
    @PostMapping("/lore/search")
    public Result<List<LoreSearchHit>> loreSearch(@RequestBody @Valid LoreSearchRequest request) {
        String query = request.query();
        return Result.success(knowledgeBaseService.search(query));
    }

    /**
     * P1 — 列出所有可用 Skill。
     * <p>供前端"技能切换"下拉框展示。返回 id/name/description/triggers。</p>
     */
    @Operation(summary = "列出全部 Skill", description = "供前端技能切换下拉框展示")
    @GetMapping("/skills")
    public Result<List<SkillInfo>> skills() {
        List<SkillInfo> list = skillRegistry.list().stream()
                .map(s -> SkillInfo.builder()
                        .id(s.id())
                        .name(s.name())
                        .description(s.description())
                        .triggers(s.triggers())
                        .priority(s.priority())
                        .build())
                .toList();
        return Result.success(list);
    }

    /**
     * P1 — 预览某段文本会命中哪个 Skill。
     * <p>用于前端"自动匹配提示":作者粘贴大纲时,实时展示会被激活的 Skill。</p>
     */
    @Operation(summary = "Skill 预览", description = "预览某段文本会命中哪个 Skill")
    @PostMapping("/skills/preview")
    public Result<SkillInfo> skillsPreview(@RequestBody @Valid SkillPreviewRequest request) {
        String text = request.text() != null ? request.text() : "";
        String forceSkillId = request.skillId();
        Skill skill = skillRegistry.resolve(text, forceSkillId);
        return Result.success(SkillInfo.builder()
                .id(skill.id())
                .name(skill.name())
                .description(skill.description())
                .build());
    }
}
