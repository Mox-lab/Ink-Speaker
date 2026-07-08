package com.ink.speaker.ai.domain.agent;

import lombok.Builder;

/**
 * 设定问答响应 DTO。
 */
@Builder
public record LoreResponse(String answer) {
}
