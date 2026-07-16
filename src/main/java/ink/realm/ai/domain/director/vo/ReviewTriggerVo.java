package ink.realm.ai.domain.director.vo;

import lombok.Builder;

/**
 * 章节审查触发结果。
 */
@Builder
public record ReviewTriggerVo(
        boolean success,
        String message) {
}
