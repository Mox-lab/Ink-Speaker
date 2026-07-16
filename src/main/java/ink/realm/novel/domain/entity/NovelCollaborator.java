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
 * 小说协作者实体(BASE-11 多用户协作)。
 * <p>对应表 novel_collaborator。每条记录表示"某用户对某本小说的协作授权"。</p>
 *
 * <p>角色语义:</p>
 * <ul>
 *   <li>{@code editor} — 可读 + 可编辑(章节/大纲/人物/设定)</li>
 *   <li>{@code viewer} — 只读,不可修改任何内容</li>
 * </ul>
 *
 * <p>owner 始终拥有最高权限,不在本表中;owner 通过 {@code novel.owner_id} 体现。</p>
 * <p>唯一约束 (novel_id, user_id) 由 Flyway 在库表层维护。</p>
 *
 * @author songshan.li
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "小说协作者实体(多用户协作)")
@TableName(value = "novel_collaborator")
public class NovelCollaborator extends BaseEntity {

    /** 所属小说 ID,NOT NULL。 */
    @Schema(description = "所属小说 ID")
    private Long novelId;

    /** 用户 ID,NOT NULL。 */
    @Schema(description = "协作者用户 ID")
    private Long userId;

    /** 协作角色:editor / viewer,NOT NULL,默认 editor。 */
    @Schema(description = "协作角色:editor / viewer")
    @Builder.Default
    private String role = "editor";
}
