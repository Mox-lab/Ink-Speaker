package ink.realm.ai.domain.agent;

import jakarta.validation.constraints.NotBlank;

/**
 * 润色请求 DTO(/api/polish)。
 */
public record PolishRequest(
        @NotBlank String draft,
        String focus,
        String intensity) {
}
