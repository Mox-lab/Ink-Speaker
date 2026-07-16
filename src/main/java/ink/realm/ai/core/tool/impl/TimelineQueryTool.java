package ink.realm.ai.core.tool.impl;

import ink.realm.ai.core.tool.AiTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ink.realm.common.context.NovelContext;
import org.springframework.stereotype.Component;

/**
 * 工具:查询剧情时间线。
 * <p>查询已发生的剧情节点,避免剧情穿帮、时间线冲突。</p>
 * <p>novelId 来自 {@link NovelContext}。</p>
 *
 * <p><b>重要约束:</b>本类<b>不能</b>被 Spring AOP 代理(原因同
 * {@link CharacterQueryTool})。缓存与 DB 查询已下沉到
 * {@link TimelineQueryToolCache}。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimelineQueryTool implements AiTool {

    private final TimelineQueryToolCache cache;

    @Tool(name = "queryTimeline", value = {
            "查询已发生的剧情节点。当需要回顾前情、衔接前后章节、避免剧情冲突时调用此工具。"})
    public String queryTimeline(
            @P("章节序号或关键词,例如 第3章、云陵城相遇") String keyword) {
        return cache.query(keyword);
    }
}
