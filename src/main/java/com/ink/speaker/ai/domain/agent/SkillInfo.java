package com.ink.speaker.ai.domain.agent;

import lombok.Builder;

/**
 * Skill 简要信息(/api/skills, /api/skills/preview)。
 */
@Builder
public record SkillInfo(
        String id,
        String name,
        String description,
        java.util.List<String> triggers,
        Integer priority) {
}
