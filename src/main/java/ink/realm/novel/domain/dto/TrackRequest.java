package ink.realm.novel.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 漏斗事件上报请求(UX-11)。
 *
 * @param eventType 事件类型,例如 funnel.login / funnel.save_chapter
 * @param novelId   小说 id(可选),若与具体小说相关则填
 * @param props     附加属性(可选),前端自由传入
 */
public record TrackRequest(
        @NotBlank @Size(max = 50) String eventType,
        Long novelId,
        Map<String, Object> props) {
}
