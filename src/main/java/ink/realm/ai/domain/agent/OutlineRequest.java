package ink.realm.ai.domain.agent;

import jakarta.validation.constraints.Min;

/**
 * 大纲生成请求 DTO(/api/outline、/api/outline/plan、/api/outline/volume)。
 * <p>兼容前端只传 theme 的简化场景:theme 兜底为 blueprint,setting 留空。</p>
 * <p>卷结构规划不再传入"目标章节数",各卷章数由模型依据该卷剧情体量自行决定(每卷建议 100-300 章量级,可浮动;全书总量为各卷之和)。</p>
 *
 * @param blueprint     蓝图(与 theme 二选一)
 * @param theme         主题(blueprint 为空时用作蓝图)
 * @param setting       设定文本(可空)
 * @param volumePlan    自定义卷规划(可空);传入则跳过自动规划,直接据此展开各卷细纲
 * @param volumeIndex   展开单卷时的卷号(配合 /api/outline/volume 使用)
 */
public record OutlineRequest(
        String blueprint,
        String theme,
        String setting,
        String volumePlan,
        @Min(1) Integer volumeIndex) {
}
