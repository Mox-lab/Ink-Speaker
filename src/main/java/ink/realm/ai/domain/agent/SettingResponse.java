package ink.realm.ai.domain.agent;

import lombok.Builder;

/**
 * 设定阶段响应 DTO。
 */
@Builder
public record SettingResponse(
        String blueprint,
        String setting) {
}
