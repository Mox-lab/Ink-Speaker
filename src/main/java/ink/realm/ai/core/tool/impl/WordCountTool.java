package ink.realm.ai.core.tool.impl;

import ink.realm.ai.core.tool.AiTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 工具:字数统计。
 * <p>LLM 数不清字数,通过工具精确统计,满足"3000 字章节"这类硬要求。</p>
 */
@Slf4j
@Component
public class WordCountTool implements AiTool {

    @Tool(name = "countWords", value = {
            "统计给定文本的字符数(含标点)。当需要确认章节字数、检查是否达到目标长度时调用此工具。"})
    public String countWords(@P("待统计的文本内容") String text) {
        log.info("[Tool] countWords len={}", text == null ? 0 : text.length());
        if (text == null || text.isEmpty()) {
            return "字数: 0";
        }
        int chars = text.length();
        int charsNoSpace = text.replaceAll("\\s", "").length();
        return String.format("字数统计: 总字符 %d, 去空白 %d", chars, charsNoSpace);
    }
}
