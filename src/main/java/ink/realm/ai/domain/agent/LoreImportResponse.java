package ink.realm.ai.domain.agent;

import lombok.Builder;

/**
 * 知识库导入响应 DTO。
 */
@Builder
public record LoreImportResponse(
        boolean success,
        int added,
        String msg) {
}
