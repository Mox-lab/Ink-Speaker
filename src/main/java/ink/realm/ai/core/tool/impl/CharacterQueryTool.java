package ink.realm.ai.core.tool.impl;

import ink.realm.ai.core.tool.AiTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ink.realm.common.context.NovelContext;
import org.springframework.stereotype.Component;

/**
 * 工具:查询人物档案。
 * <p>LLM 写到某个人物时调用,确保人设一致。先精确匹配,再模糊匹配。</p>
 * <p>novelId 来自 {@link NovelContext}
 * (由 {@code NovelContextFilter} 从 X-Novel-Id 头注入)。</p>
 *
 * <p><b>重要约束:</b>本类<b>不能</b>被 Spring AOP 代理。原实现将
 * {@code @Cacheable} 与 {@code @Tool} 标在同一方法上,Spring 会为其生成
 * CGLIB 代理;而 CGLIB 代理子类重写的 {@code @Tool} 方法<b>不会继承注解</b>,
 * 导致 LangChain4j 的 AiServices 报
 * "does not have any methods annotated with @Tool"。因此缓存与 DB 查询已
 * 下沉到 {@link CharacterQueryToolCache},本类保持零 AOP 切面、无代理。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CharacterQueryTool implements AiTool {

    private final CharacterQueryToolCache cache;

    @Tool(name = "queryCharacter", value = {
            "根据人物姓名查询其档案(年龄/性格/外貌/武器/背景)。当需要描写某个角色的言行、确保人设不崩塌时调用此工具。"})
    public String queryCharacter(
            @P("人物姓名,例如 林晚、苏砚、赵九") String name) {
        return cache.query(name);
    }
}
