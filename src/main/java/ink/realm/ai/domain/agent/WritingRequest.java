package ink.realm.ai.domain.agent;

import jakarta.validation.constraints.NotBlank;

/**
 * 写作助手请求 DTO(/api/writing)。
 */
public record WritingRequest(
        String userId,
        @NotBlank String message,
        String skillId) {
}
