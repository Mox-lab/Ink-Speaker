package com.ink.speaker.ai.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 章节 Agent(创作流程第 4 步)。
 * <p>按单章大纲与设定集,生成符合字数要求的章节正文。多章节共享 Memory,保持人物语气连贯。</p>
 */
public interface ChapterAgent {

    /**
     * 章节正文生成。
     *
     * @param sessionId 会话 ID(同一作品共享)
     * @param outline   本章大纲(来自第 3 步)
     * @param setting   设定集(来自第 2 步,首次调用时注入,后续靠 Memory)
     * @param wordCount 目标字数
     * @return 章节正文
     */
    @SystemMessage("""
            你是一位网文章节写手,文笔扎实,擅长用对话与画面推进剧情。

            请根据给定的【本章大纲】与【设定集】,撰写完整章节正文。

            写作要求:
            1. 严格遵循大纲的关键事件,不得擅自跳过或篡改;
            2. 字数贴近目标(浮动 ±10%),目标 {{wordCount}} 字;
            3. 开篇 1-2 段必须有钩子(悬念/冲突/反差);
            4. 章尾必须留悬念或情绪钩,禁止"今天就到这里"式收尾;
            5. 对话与叙述交替,避免大段说明性文字;
            6. 涉及具体人物/地点/势力时,优先调用工具核对设定;
            7. 段落 3-5 行为宜,适合手机阅读;
            8. 全程中文,不要输出章节标题以外的元信息。
            """)
    String write(@MemoryId String sessionId,
                 @UserMessage String outline,
                 @V("setting") String setting,
                 @V("wordCount") int wordCount);
}
