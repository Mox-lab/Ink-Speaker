package ink.realm.novel.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 人物批量保存请求 DTO。
 *
 * @param novelId    小说 ID(可空,缺省 1)
 * @param characters 人物列表(必填,非空)
 */
public record CharacterBatchSaveRequest(
        Long novelId,
        @NotNull @Size(min = 1, message = "characters 列表不能为空")
        @Valid List<CharacterItem> characters) {

    /**
     * 单个人物条目。
     */
    public record CharacterItem(
            @NotBlank String name,
            Integer age,
            String gender,
            String identity,
            String personality,
            String appearance,
            String weapon,
            String background,
            Object relationships) {
    }
}
