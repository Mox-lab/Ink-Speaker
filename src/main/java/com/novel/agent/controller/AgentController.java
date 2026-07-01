package com.novel.agent.controller;

import com.novel.agent.agent.ChapterAgent;
import com.novel.agent.agent.CharacterExtractionAgent;
import com.novel.agent.agent.LoreAgent;
import com.novel.agent.agent.OutlineAgent;
import com.novel.agent.agent.WritingAssistantAgent;
import com.novel.agent.service.KnowledgeBaseService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 小说创作 Agent REST 接口
 * <p>
 * 提供一组接口,演示写作 Agent 的核心能力:
 *   - 普通对话(单轮,直接调 ChatModel)
 *   - 流式对话(打字机效果,适合长文生成)
 *   - 写作助手(多轮记忆 + 工具调用)
 *   - 大纲生成
 *   - 章节生成
 *   - 人物抽取(结构化输出)
 *   - 设定问答(RAG)
 *   - 记忆能力测试
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AgentController {

    private final ChatLanguageModel chatModel;
    private final StreamingChatLanguageModel streamingChatModel;
    private final WritingAssistantAgent writingAssistantAgent;
    private final OutlineAgent outlineAgent;
    private final ChapterAgent chapterAgent;
    private final CharacterExtractionAgent characterExtractionAgent;
    private final LoreAgent loreAgent;
    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 1. 普通对话(单轮,无记忆)
     * <p>
     * 直接调用 ChatModel,适合简单问答场景。
     * </p>
     */
    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        log.info("[/chat] 用户输入: {}", message);
        String reply = chatModel.chat(message);
        return Map.of("reply", reply);
    }

    /**
     * 2. 流式对话(打字机效果,基于 SSE)
     * <p>
     * Server-Sent Events,前端可用 EventSource 接收。
     * 体验上类似 ChatGPT 的逐字输出,长文生成必备。
     * </p>
     */
    @PostMapping(value = "/chat/stream", produces = "text/event-stream")
    public SseEmitter chatStream(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        SseEmitter emitter = new SseEmitter(120_000L);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            streamingChatModel.chat(message, new StreamingChatResponseHandler() {
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
            });
        });
        executor.shutdown();
        return emitter;
    }

    /**
     * 3. 写作助手(多轮记忆 + 工具调用)
     * <p>
     * 演示完整写作 Agent 能力。试试问:
     *   - "帮我写林晚与苏砚在云陵城码头的初遇,1500 字"
     *   - "查询一下林晚的人物档案"
     *   - "扩写场景:雨夜 林晚在码头等苏砚"
     *   - "计算一下这段文字的字数: ..."
     * </p>
     */
    @PostMapping("/writing")
    public Map<String, String> writing(@RequestBody Map<String, String> body) {
        String userId = body.getOrDefault("userId", "writer-001");
        String message = body.get("message");
        log.info("[/writing] userId={}, message={}", userId, message);
        String reply = writingAssistantAgent.chat(userId, message);
        return Map.of("userId", userId, "reply", reply);
    }

    /**
     * 4. 大纲生成
     * <p>
     * 输入题材描述与目标章节数,返回 markdown 大纲。
     * </p>
     */
    @PostMapping("/outline")
    public Map<String, String> outline(@RequestBody Map<String, Object> body) {
        String theme = (String) body.get("theme");
        int chapters = body.containsKey("chapters") ? ((Number) body.get("chapters")).intValue() : 20;
        log.info("[/outline] theme={}, chapters={}", theme, chapters);
        String outlineText = outlineAgent.generate(theme, chapters);
        return Map.of("theme", theme, "outline", outlineText);
    }

    /**
     * 5. 章节生成
     * <p>
     * 输入本章大纲与目标字数,生成完整章节正文。
     * </p>
     */
    @PostMapping("/chapter")
    public Map<String, String> chapter(@RequestBody Map<String, Object> body) {
        String sessionId = (String) body.getOrDefault("sessionId", "novel-001");
        String outline = (String) body.get("outline");
        int wordCount = body.containsKey("wordCount") ? ((Number) body.get("wordCount")).intValue() : 2000;
        log.info("[/chapter] sessionId={}, wordCount={}", sessionId, wordCount);
        String content = chapterAgent.write(sessionId, outline, wordCount);
        return Map.of("sessionId", sessionId, "content", content);
    }

    /**
     * 6. 人物抽取(结构化输出)
     * <p>
     * 把一段人物描写转成结构化人物卡。
     * </p>
     */
    @PostMapping("/character")
    public CharacterExtractionAgent.CharacterProfile character(@RequestBody Map<String, String> body) {
        String text = body.get("text");
        log.info("[/character] text={}", text);
        return characterExtractionAgent.extract(text);
    }

    /**
     * 7. 设定问答(RAG)
     * <p>
     * 使用前请先调用 /api/lore/import 导入设定文档。
     * </p>
     */
    @PostMapping("/lore")
    public Map<String, String> lore(@RequestBody Map<String, String> body) {
        String sessionId = body.getOrDefault("sessionId", "lore-001");
        String question = body.get("question");
        log.info("[/lore] question={}", question);
        String answer = loreAgent.ask(sessionId, question);
        return Map.of("answer", answer);
    }

    /**
     * 7.1 导入设定库
     * <p>
     * 从指定目录加载文档;也可直接传入文本字符串快速演示。
     * </p>
     */
    @PostMapping("/lore/import")
    public Map<String, Object> importLore(@RequestBody Map<String, String> body) {
        String dir = body.get("dir");
        String text = body.get("text");

        if (text != null && !text.isBlank()) {
            knowledgeBaseService.addText(text, "manual");
            return Map.of("success", true, "added", 1);
        }
        if (dir != null) {
            int count = knowledgeBaseService.importDocuments(dir);
            return Map.of("success", true, "added", count);
        }
        return Map.of("success", false, "msg", "请传入 dir 或 text 参数");
    }

    /**
     * 8. 多轮记忆测试
     * <p>
     * 不带工具,纯验证 Memory 能力。连续调用两次,第二次提问依赖第一次的内容。
     * </p>
     */
    @GetMapping("/memory")
    public Map<String, String> memory() {
        String userId = "memory-test-" + UUID.randomUUID();
        String r1 = writingAssistantAgent.chat(userId, "我正在写一本东方玄幻小说,主角叫林晚,24 岁,孤儿");
        String r2 = writingAssistantAgent.chat(userId, "我刚才告诉你主角叫什么?多大?");
        return Map.of(
                "userId", userId,
                "round1", r1,
                "round2", r2,
                "note", "Agent 应在 round2 中正确回答出'林晚,24 岁'"
        );
    }

    /**
     * 9. 检索调试:查看设定库检索结果
     */
    @PostMapping("/lore/search")
    public List<Map<String, Object>> loreSearch(@RequestBody Map<String, String> body) {
        String query = body.get("query");
        return knowledgeBaseService.search(query).stream()
                .map(m -> Map.<String, Object>of(
                        "score", m.score(),
                        "text", m.embedded().text()
                ))
                .toList();
    }
}
