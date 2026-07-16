package ink.realm.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import ink.realm.common.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 世界观设定实体。
 * <p>对应表 novel_world_setting,记录地理/势力/武学/历史等设定,供 LLM 通过 queryWorldSetting 工具查询。</p>
 * <p>唯一约束 (novel_id, keyword) 由 Flyway 在库表层维护。</p>
 *
 * @author songshan.li
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "世界观设定实体")
@TableName(value = "novel_world_setting")
public class NovelWorldSetting extends BaseEntity {

    /** 所属小说 ID,NOT NULL。 */
    @Schema(description = "所属小说 ID")
    private Long novelId;

    /** 关键词,NOT NULL。 */
    @Schema(description = "设定关键词")
    private String keyword;

    /** 分类。 */
    @Schema(description = "分类(地理/势力/武学/历史)")
    private String category;

    /** 描述。 */
    @Schema(description = "描述")
    private String description;
}
