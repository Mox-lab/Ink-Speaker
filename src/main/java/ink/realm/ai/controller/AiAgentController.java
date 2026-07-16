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

import java.util.List;
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

    private static final int OUTLINE_BATCH_SIZE = 5;
    private static final int OUTLINE_TAIL_HINT_CHARS = 1500;
    private static final long SSE_TIMEOUT_MS = 120_000L;

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
     * 阶段 3 / 5 — 大纲。
     * <p>给定蓝图、设定、章节数,生成卷/章两级大纲。
     * chapters > 30 时分批生成,避免单次请求超长触发上游 504。</p>
     * <p>支持「续生」:传入 lastOutline(已有大纲尾部)和 startChapter(续生起始章号)时,
     * LLM 会接续在已有大纲之后生成,而不是从头重写。</p>
     */
    @Operation(summary = "阶段 3/5 大纲", description = "分批生成大纲,支持续生")
    @PostMapping("/outline")
    public Result<OutlineResponse> outline(@RequestBody @Valid OutlineRequest request) {
        String blueprint = resolveBlueprint(request);
        String setting = request.setting() != null ? request.setting() : "";
        int chapters = request.chapters() != null ? request.chapters() : 20;
        int startChapter = request.startChapter() != null ? request.startChapter() : 1;
        String lastOutline = request.lastOutline();
        boolean isContinue = lastOutline != null && !lastOutline.isBlank() && startChapter > 1;
        log.info("[/outline] chapters={}, startChapter={}, continue={}", chapters, startChapter, isContinue);

        try {
            String outlineText = generateOutlineBatches(blueprint, setting, chapters, startChapter, lastOutline, isContinue);
            if (outlineText.isBlank()) {
                log.warn("[/outline] all batches empty, likely finish_reason=length (reasoning burned tokens)");
                return Result.success(OutlineResponse.builder()
                        .chapters(chapters)
                        .error("大纲生成失败:模型输出为空(可能因思维链烧光 token,请调高 max-tokens 或减少章节数后重试)")
                        .build());
            }
            return Result.success(OutlineResponse.builder()
                    .chapters(chapters)
                    .outline(outlineText)
                    .startChapter(startChapter)
                    .continued(isContinue)
                    .build());
        } catch (Exception e) {
            log.error("[/outline] fail chapters={}", chapters, e);
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "大纲生成失败:" + ArgsUtil.reasonOf(e));
        }
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
     * 分批调用 outlineAgent 生成完整大纲文本。
     * <p>每批 OUTLINE_BATCH_SIZE 章,单次 LLM 调用控制在 60s 内避免上游 nginx 504。</p>
     */
    private String generateOutlineBatches(String blueprint, String setting, int chapters,
                                          int startChapter, String lastOutline, boolean isContinue) {
        StringBuilder sb = new StringBuilder();
        int cursor = startChapter;
        int endChapter = startChapter + chapters - 1;
        String tailHint = isContinue
                ? "\n\n【前文大纲尾部(请接续,不要重复)】\n"
                        + ArgsUtil.truncateTail(lastOutline, OUTLINE_TAIL_HINT_CHARS)
                : "";

        while (cursor <= endChapter) {
            int end = Math.min(cursor + OUTLINE_BATCH_SIZE - 1, endChapter);
            int span = end - cursor + 1;
            String segHint = blueprint + tailHint
                    + "\n(本次只生成第 " + cursor + " - " + end + " 章"
                    + (isContinue ? ",接续在已生成内容之后" : "")
                    + ",共 " + chapters + " 章,必须连续编号,不要重复前面内容)";
            log.info("[/outline] batch {}-{} (span={})", cursor, end, span);
            String seg = llmCacheService.generateOutlineBatch(segHint, setting, span);
            if (seg == null || seg.isBlank()) {
                log.warn("[/outline] batch {}-{} returned empty, likely finish_reason=length", cursor, end);
            } else {
                sb.append(seg).append("\n\n");
            }
            cursor = end + 1;
        }
        return sb.toString();
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
