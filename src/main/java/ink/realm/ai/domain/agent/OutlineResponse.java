package ink.realm.ai.domain.agent;

import lombok.Builder;

/**
 * 大纲生成响应 DTO。
 *
 * @param chapters   章节数
 * @param outline    大纲全文(卷-章两级);单卷展开时为该卷逐章细纲 markdown
 * @param error      失败原因(成功时为 null)
 * @param volumePlan 卷规划文本(可编辑后回传 /api/outline 的 volumePlan 复用)
 * @param volumes    规划卷数(/api/outline/volume 时为当前卷号)
 */
@Builder
public record OutlineResponse(
        Integer chapters,
        String outline,
        String error,
        String volumePlan,
        Integer volumes) {
}
