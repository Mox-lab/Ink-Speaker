package ink.realm.novel.domain.dto;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 大纲详情 DTO(含正文全文)。
 */
@Builder
public record OutlineDetail(
        Long id,
        Long novelId,
        String title,
        String theme,
        Integer chapters,
        String content,
        Integer version,
        Boolean isActive,
        LocalDateTime createdAt) {
}
