package com.ink.speaker.ai.domain.agent;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 大纲生成请求 DTO(/api/outline)。
 * <p>兼容前端只传 theme 的简化场景:theme 兜底为 blueprint,setting 留空。</p>
 *
 * @param blueprint     蓝图(与 theme 二选一)
 * @param theme         主题(blueprint 为空时用作蓝图)
 * @param setting       设定文本(可空)
 * @param chapters      章节数(缺省 20)
 * @param lastOutline   续生时已有大纲尾部(可空)
 * @param startChapter  续生起始章号(缺省 1)
 */
public record OutlineRequest(
        String blueprint,
        String theme,
        String setting,
        @NotNull @Min(1) Integer chapters,
        String lastOutline,
        @Min(1) Integer startChapter) {
}
