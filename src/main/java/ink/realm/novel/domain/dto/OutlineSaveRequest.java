package ink.realm.novel.domain.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 大纲保存请求 DTO。
 */
public record OutlineSaveRequest(
        Long novelId,
        String title,
        String theme,
        Integer chapters,
        @NotBlank String content) {
}
