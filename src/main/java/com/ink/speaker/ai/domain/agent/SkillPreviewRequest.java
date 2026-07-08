package com.ink.speaker.ai.domain.agent;

/**
 * Skill 预览请求 DTO(/api/skills/preview)。
 */
public record SkillPreviewRequest(
        String text,
        String skillId) {
}
