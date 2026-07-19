package ink.realm.novel.domain.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 世界观设定保存请求 DTO。
 *
 * @param id          主键(编辑态传入:按主键就地更新,改名也不会产生重复行;新增态为 null)
 * @param novelId     小说 ID(可空,缺省 1)
 * @param keyword     关键词(必填)
 * @param category    分类
 * @param description 描述
 */
public record SettingSaveRequest(
        Long id,
        Long novelId,
        @NotBlank String keyword,
        String category,
        String description) {
}
