package ink.realm.novel.domain.dto;

import lombok.Builder;

/**
 * 大纲保存返回结果(返回新版本号与主键)。
 */
@Builder
public record OutlineSaveResult(
        Long id,
        Integer version) {
}
