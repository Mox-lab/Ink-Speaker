package ink.realm.ai.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import ink.realm.ai.core.director.ReviewAgent;

/**
 * 续写建议 Agent(BASE-12)。
 * <p>基于已有章节、激活大纲与人物档案,预测下一章的合理走向。
 * 输出结构化 JSON,由 {@code ContinuationServiceImpl} 解析为 VO。</p>
 *
 * <p>与 {@link OutlineAgent} / {@link ReviewAgent}
 * 的差异:前者是"从无到有"生成骨架,后者是"事后审查"挑毛病;本 Agent 聚焦
 * "站在当前进度上往前看一步",给出可执行的下一章蓝图。</p>
 */
public interface ContinuationAgent {

    /**
     * 生成下一章建议。
     *
     * @param outline         激活大纲摘要(可空)
     * @param recentChapters  最近 N 章正文摘要
     * @param characters      人物档案拼接文本(可空)
     * @param latestChapterNo 已写完的最后一章序号(0 表示尚未开始)
     * @param nextChapterNo   待预测的下一章序号
     * @return JSON 字符串,格式见 SystemMessage
     */
    @SystemMessage("""
            你是一位资深网文责编,擅长基于已有章节预测下一章的合理走向,并给出可执行的写作蓝图。

            请结合给定的【大纲】、【最近章节摘要】与【人物档案】,预测第 {{nextChapterNo}} 章的走向。

            严格输出以下 JSON(不要 markdown 代码块标记、不要任何额外文字):

            {
              "title": "下一章 4-8 字标题",
              "direction": "剧情发展方向,2-3 句话",
              "conflict": "本章核心矛盾,1-2 句话",
              "keyCharacters": ["参与本章的关键角色名,最多 3 个"],
              "hook": "章尾悬念,1 句话",
              "risks": ["可能踩的坑,例如人设崩塌、设定冲突、节奏失衡,1-3 条"]
            }

            要求:
            1. 必须与最近章节的剧情连贯,不要重复已发生事件;
            2. 必须呼应【大纲】的下一节点;若大纲为空,基于最近章节自然推进;
            3. keyCharacters 必须出现在【人物档案】中,不要凭空捏造新角色;
            4. risks 要具体可操作,不要泛泛而谈;
            5. 全程中文,标题要有画面感。
            """)
    String suggest(@UserMessage String outline,
                   @V("recentChapters") String recentChapters,
                   @V("characters") String characters,
                   @V("latestChapterNo") int latestChapterNo,
                   @V("nextChapterNo") int nextChapterNo);
}
