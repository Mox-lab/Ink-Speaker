package com.ink.speaker.ai.domain.agent;

import jakarta.validation.constraints.NotBlank;

/**
 * 设定阶段请求 DTO(/api/setting)。
 */
public record SettingRequest(
        @NotBlank String blueprint,
        String tone) {
}
