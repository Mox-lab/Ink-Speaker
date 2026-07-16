package ink.realm.novel.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 小说更新请求 DTO。
 * <p>仅允许修改 title / author / description / sharedForReference,不可改 ownerId。</p>
 *
 * @param title              小说标题(必填,1-200 字符)
 * @param author             作者(可空,最长 100 字符)
 * @param description        简介(可空)
 * @param sharedForReference 是否公开到公共参考池
 */
public record NovelUpdateRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 100) String author,
        String description,
        boolean sharedForReference) {
}
