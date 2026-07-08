package com.ink.speaker.ai.core.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Skill 注册与激活中心。
 * <p>Spring 自动注入所有 {@link Skill} Bean,提供:</p>
 * <ul>
 *   <li>{@link #list()}:列出所有已注册 Skill(供前端展示与切换)</li>
 *   <li>{@link #resolve(String)}:根据当前文本(大纲/主题/用户输入)自动匹配最合适的 Skill</li>
 *   <li>{@link #getById(String)}:按 id 取指定 Skill(供前端手动切换)</li>
 * </ul>
 *
 * <p>当配置 {@code ink-speaker.skill.enabled=false} 时,Skill 系统退化为
 * 永远返回 {@code default-skill} 指定的 Skill(默认即 {@code default}),不参与自动匹配。</p>
 */
@Slf4j
@Component
public class SkillRegistry {

    private final List<Skill> skills;
    private final boolean enabled;
    private final String defaultSkillId;

    public SkillRegistry(
            List<Skill> skills,
            @Value("${ink-speaker.skill.enabled:true}") boolean enabled,
            @Value("${ink-speaker.skill.default-skill:default}") String defaultSkillId) {
        this.skills = skills;
        this.enabled = enabled;
        this.defaultSkillId = defaultSkillId;
        log.info("[SkillRegistry] 注册技能 {} 个:{}, enabled={}, default={}",
                skills.size(),
                skills.stream().map(Skill::id).toList(),
                enabled,
                defaultSkillId);
    }

    /** 列出全部 Skill(按 id 排序,只读视图)。 */
    public List<Skill> list() {
        return skills.stream()
                .sorted(Comparator.comparing(Skill::id))
                .toList();
    }

    /** 按 id 取 Skill,找不到返回空 Optional。 */
    public Optional<Skill> getById(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return skills.stream().filter(s -> s.id().equals(id)).findFirst();
    }

    /**
     * 解析当前上下文应激活的 Skill。
     * <p>策略:</p>
     * <ol>
     *   <li>若 disabled,直接返回 default</li>
     *   <li>从全部 Skill 中找出 triggers 命中当前文本的,按 priority 降序取第一个</li>
     *   <li>若无命中,返回 default</li>
     * </ol>
     *
     * @param context 当前文本(大纲 / 主题 / 用户输入等),可为 null
     * @return 命中的 Skill;永远非空
     */
    public Skill resolve(String context) {
        if (!enabled || context == null || context.isBlank()) {
            return getById(defaultSkillId).orElseThrow();
        }

        String lower = context.toLowerCase();
        List<Skill> hits = new ArrayList<>();
        for (Skill s : skills) {
            if (s.id().equals(defaultSkillId)) continue;
            for (String kw : s.triggers()) {
                if (kw != null && !kw.isBlank() && lower.contains(kw.toLowerCase())) {
                    hits.add(s);
                    break;
                }
            }
        }
        if (hits.isEmpty()) {
            return getById(defaultSkillId).orElseThrow();
        }
        hits.sort(Comparator.comparingInt(Skill::priority).reversed());
        return hits.get(0);
    }

    /**
     * 解析当前上下文应激活的 Skill;若与强制指定 id 冲突,以指定为准。
     * <p>用于"前端手动选定 → 不被自动匹配覆盖"的场景。</p>
     */
    public Skill resolve(String context, String forceSkillId) {
        if (forceSkillId != null && !forceSkillId.isBlank()) {
            return getById(forceSkillId).orElseGet(() -> resolve(context));
        }
        return resolve(context);
    }
}
