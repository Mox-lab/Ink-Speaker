package com.novel.forge.controller;

import com.novel.forge.agent.ChapterAgent;
import com.novel.forge.agent.CharacterExtractionAgent;
import com.novel.forge.agent.LoreAgent;
import com.novel.forge.agent.OutlineAgent;
import com.novel.forge.agent.WritingAssistantAgent;
import com.novel.forge.service.KnowledgeBaseService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
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
 * 小说创作 Agent REST 接口。
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AgentController {

    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;
    private final WritingAssistantAgent writingAssistantAgent;
    private final OutlineAgent outlineAgent;
    private final ChapterAgent chapterAgent;
    private final CharacterExtractionAgent characterExtractionAgent;
    private final LoreAgent loreAgent;
    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 1. 普通对话(单轮,无记忆)。
     *
     * @param body 请求体,需包含 message 字段
     * @return Map:{"reply": "模型回复"}
     */
    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody Map<String, String> body) {
        String message = body.get("message");              // 取出用户输入
        log.info("[/chat] 用户输入: {}", message);          // 记录请求日志
        String reply = chatModel.chat(message);            // 直接调 LLM,无记忆
        return Map.of("reply", reply);                     // 返回单条回复
    }

    /**
     * 2. 流式对话(SSE 打字机效果)。
     *
     * @param body 请求体,需包含 message 字段
     * @return SseEmitter,前端用 EventSource 接收逐字输出
     */
    @PostMapping(value = "/chat/stream", produces = "text/event-stream")
    public SseEmitter chatStream(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        SseEmitter emitter = new SseEmitter(120_000L);     // 120 秒超时
        ExecutorService executor = Executors.newSingleThreadExecutor();  // 单线程异步推送

        executor.execute(() -> {
            // 调用流式模型,传入回调处理器接收分片
            streamingChatModel.chat(message, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    // 每收到一个分片,通过 SSE 推给前端
                    try {
                        emitter.send(SseEmitter.event().data(partialResponse));
                    } catch (Exception e) {
                        emitter.completeWithError(e);      // 发送失败则关闭连接
                    }
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    emitter.complete();                    // 模型输出结束,关闭 SSE
                }

                @Override
                public void onError(Throwable error) {
                    emitter.completeWithError(error);      // 出错时把错误传给前端
                }
            });
        });
        executor.shutdown();                               // 任务提交后关闭线程池
        return emitter;                                    // 返回 emitter,Spring 保持连接
    }

    /**
     * 3. 写作助手(多轮记忆 + 工具调用)。
     *
     * @param body 请求体,可选 userId(默认 writer-001),必含 message
     * @return Map:{"userId": "...", "reply": "..."}
     */
    @PostMapping("/writing")
    public Map<String, String> writing(@RequestBody Map<String, String> body) {
        String userId = body.getOrDefault("userId", "writer-001");  // 默认会话 ID
        String message = body.get("message");
        log.info("[/writing] userId={}, message={}", userId, message);
        String reply = writingAssistantAgent.chat(userId, message); // 多轮 Agent 调用
        return Map.of("userId", userId, "reply", reply);
    }

    /**
     * 4. 大纲生成。
     *
     * @param body 请求体,必含 theme,可选 chapters(默认 20)
     * @return Map:{"theme": "...", "outline": "markdown 文本"}
     */
    @PostMapping("/outline")
    public Map<String, String> outline(@RequestBody Map<String, Object> body) {
        String theme = (String) body.get("theme");
        // chapters 可能是 Integer/Long/Number,统一用 Number.intValue() 取整
        int chapters = body.containsKey("chapters") ? ((Number) body.get("chapters")).intValue() : 20;
        log.info("[/outline] theme={}, chapters={}", theme, chapters);
        String outlineText = outlineAgent.generate(theme, chapters);
        return Map.of("theme", theme, "outline", outlineText);
    }

    /**
     * 5. 章节生成。
     *
     * @param body 请求体,必含 outline,可选 sessionId(默认 novel-001)、wordCount(默认 2000)
     * @return Map:{"sessionId": "...", "content": "章节正文"}
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
     * 6. 人物抽取(结构化输出)。
     *
     * @param body 请求体,必含 text(待抽取的人物描写)
     * @return CharacterProfile 结构化人物卡
     */
    @PostMapping("/character")
    public CharacterExtractionAgent.CharacterProfile character(@RequestBody Map<String, String> body) {
        String text = body.get("text");
        log.info("[/character] text={}", text);
        return characterExtractionAgent.extract(text);     // 直接返回 record,框架自动 JSON 序列化
    }

    /**
     * 7. 设定问答(RAG)。
     *
     * @param body 请求体,必含 question,可选 sessionId(默认 lore-001)
     * @return Map:{"answer": "..."}
     */
    @PostMapping("/lore")
    public Map<String, String> lore(@RequestBody Map<String, String> body) {
        String sessionId = body.getOrDefault("sessionId", "lore-001");
        String question = body.get("question");
        log.info("[/lore] question={}", question);
        String answer = loreAgent.ask(sessionId, question);  // RAG:自动检索设定 + 生成回答
        return Map.of("answer", answer);
    }

    /**
     * 7.1 导入设定库。
     * <p>支持两种模式:传 text 直接入库;传 dir 批量加载目录文档。</p>
     *
     * @param body 请求体,可选 text 或 dir
     * @return Map:{"success": true/false, "added": 数量} 或失败时带 msg
     */
    @PostMapping("/lore/import")
    public Map<String, Object> importLore(@RequestBody Map<String, String> body) {
        String dir = body.get("dir");
        String text = body.get("text");

        if (text != null && !text.isBlank()) {
            knowledgeBaseService.addText(text, "manual");  // 直接加文本片段
            return Map.of("success", true, "added", 1);
        }
        if (dir != null) {
            int count = knowledgeBaseService.importDocuments(dir);  // 批量导入目录
            return Map.of("success", true, "added", count);
        }
        return Map.of("success", false, "msg", "请传入 dir 或 text 参数");
    }

    /**
     * 8. 多轮记忆测试(GET,无参数)。
     * <p>连续调用两次 Agent,第二次提问依赖第一次的内容,验证 Memory 是否生效。</p>
     *
     * @return Map:包含 userId、两轮回复以及 note(预期结果)
     */
    @GetMapping("/memory")
    public Map<String, String> memory() {
        String userId = "memory-test-" + UUID.randomUUID();  // 每次测试用新会话 ID
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
     * 9. 检索调试:查看设定库检索结果。
     *
     * @param body 请求体,必含 query
     * @return List 形式:[{"score": 0.85, "text": "片段内容"}, ...]
     */
    @PostMapping("/lore/search")
    public List<Map<String, Object>> loreSearch(@RequestBody Map<String, String> body) {
        String query = body.get("query");
        return knowledgeBaseService.search(query).stream()  // 调用 RAG 检索
                .map(m -> Map.<String, Object>of(
                        "score", m.score(),                // 相似度分数(0~1)
                        "text", m.embedded().text()        // 片段原文
                ))
                .toList();
    }
}
