package com.ink.speaker.ai.domain.agent;

import lombok.Builder;

/**
 * 写作助手响应 DTO。
 */
@Builder
public record WritingResponse(
        String userId,
        String reply,
        String skillId,
        String skillName) {
}
