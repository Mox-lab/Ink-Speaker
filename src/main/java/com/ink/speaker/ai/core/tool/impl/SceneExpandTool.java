package com.ink.speaker.ai.core.tool.impl;

import com.ink.speaker.ai.core.tool.AiTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 工具:场景扩写建议。
 * <p>给定简短场景,返回五感维度的扩写提示,帮助 LLM 在卡文时找到方向。</p>
 */
@Slf4j
@Component
public class SceneExpandTool implements AiTool {

    @Tool(name = "expandScene", value = {
            "为简短场景提供感官维度的扩写建议。当用户要求扩写、需要丰富细节、卡文时调用此工具。"})
    public String expandScene(@P("简短场景描述,例如 雨夜 林晚在码头等苏砚") String scene) {
        log.info("[Tool] expandScene scene={}", scene);
        String[] senses = {"视觉", "听觉", "嗅觉", "触觉", "情绪"};
        String[] hints = {
                "雨幕如何拍打青石板?灯火如何映在水面?",
                "雨声是否盖住脚步?远处有没有更鼓?",
                "潮湿的江风带着什么气味?鱼腥还是泥土?",
                "她的衣袖湿透,贴在皮肤上是冷是黏?",
                "等待的焦灼与一丝不敢承认的期待如何交织?"
        };
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(scene).append(" 的扩写建议】\n");
        for (int i = 0; i < senses.length; i++) {
            sb.append("- ").append(senses[i]).append(": ").append(hints[i]).append("\n");
        }
        return sb.toString();
    }
}
