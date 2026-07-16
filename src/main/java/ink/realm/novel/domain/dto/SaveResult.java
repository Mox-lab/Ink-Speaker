package ink.realm.novel.domain.dto;

import lombok.Builder;

/**
 * 保存操作返回值(返回新/更新后的主键)。
 */
@Builder
public record SaveResult(Long id) {
}
