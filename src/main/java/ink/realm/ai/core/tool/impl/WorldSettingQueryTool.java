package ink.realm.ai.core.tool.impl;

import ink.realm.ai.core.tool.AiTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ink.realm.common.context.NovelContext;
import org.springframework.stereotype.Component;

/**
 * 工具:查询世界观设定。
 * <p>查询地理/势力/历史/武学体系等,避免设定矛盾。</p>
 * <p>novelId 来自 {@link NovelContext}。</p>
 *
 * <p><b>重要约束:</b>本类<b>不能</b>被 Spring AOP 代理(原因同
 * {@link CharacterQueryTool})。缓存与 DB 查询已下沉到
 * {@link WorldSettingQueryToolCache}。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorldSettingQueryTool implements AiTool {

    private final WorldSettingQueryToolCache cache;

    @Tool(name = "queryWorldSetting", value = {
            "查询世界观设定关键词(地理/势力/历史/武学体系等)。当描写某个地点、势力、规则时调用此工具确认设定。"})
    public String queryWorldSetting(
            @P("设定关键词,例如 青州、听潮阁、武学品阶") String keyword) {
        return cache.query(keyword);
    }
}
