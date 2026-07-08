package com.ink.speaker.ai.domain.agent;

import lombok.Builder;

/**
 * 设定阶段响应 DTO。
 */
@Builder
public record SettingResponse(
        String blueprint,
        String setting) {
}
