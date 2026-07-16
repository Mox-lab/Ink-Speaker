package ink.realm.ai.core.skill;

import java.util.List;

/**
 * 写作技能抽象。
 * <p>对标 cc-haha {@code src/skills/} 的"场景化写作能力"。</p>
 *
 * <p>一个 Skill 描述一种特定题材 / 文风 / 叙事结构下的写作专长,
 * 当作者当前创作主题命中触发条件时,SkillRegistry 会激活该 Skill,
 * 把 {@link #promptSuffix()} 注入到 Agent 的 system prompt 后缀,
 * 并可能限制可用工具白名单,从而让 AI 在该场景下更专业。</p>
 *
 * <p>三维分类:</p>
 * <ul>
 *   <li>题材类型:修仙 / 推理 / 言情 / 武侠 / 末世 ...</li>
 *   <li>文风:古风 / 现实 / 二次元 / 严肃文学 ...</li>
 *   <li>叙事结构:线性 / 多线 / 倒叙 / 视角切换 ...</li>
 * </ul>
 */
public interface Skill {

    /** Skill 唯一标识(kebab-case,例如 {@code xianxia-worldbuilding})。 */
    String id();

    /** 人类可读名称(中文)。 */
    String name();

    /** 简短描述,供前端展示。 */
    String description();

    /**
     * 触发关键词列表。
     * <p>SkillRegistry 会用当前大纲 / 设定 / 用户输入做包含匹配,
     * 命中任一关键词即认为该 Skill 可能适用。</p>
     * <p>空列表表示永不自动激活,只能手动指定。</p>
     */
    List<String> triggers();

    /**
     * 激活后追加到 system prompt 末尾的内容。
     * <p>应当是一段"风格化指令",告诉 AI 在这种场景下应该如何写。</p>
     */
    String promptSuffix();

    /**
     * 工具白名单(空列表表示不限制,使用全部工具)。
     * <p>非空时,只暴露此列表中名字对应的工具给 Agent。
     * 用于"推理布局"等场景屏蔽掉无关工具,降低 LLM 误用率。</p>
     */
    default List<String> toolWhitelist() {
        return List.of();
    }

    /**
     * 优先级(数字大优先)。多 Skill 命中时,取优先级最高的。
     * <p>默认 0,具体 Skill 可覆盖。</p>
     */
    default int priority() {
        return 0;
    }
}
