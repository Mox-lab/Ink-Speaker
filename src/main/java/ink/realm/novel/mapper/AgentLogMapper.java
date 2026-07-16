package ink.realm.novel.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.realm.novel.domain.entity.AgentLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 漏斗埋点 DAO(UX-11)。
 * <p>简单单表查询(listByUserIdAndDateRange)通过 {@code default} 方法 + LambdaQueryWrapper 实现;</p>
 * <p>事件类型聚合统计(GROUP BY + COUNT DISTINCT)保留 XML。</p>
 *
 * @author songshan.li
 */
@Mapper
public interface AgentLogMapper extends BaseMapper<AgentLog> {

    /** 查询指定用户在时间区间内的漏斗路径回放。 */
    default List<AgentLog> listByUserIdAndDateRange(Long userId, LocalDateTime startTime, LocalDateTime endTime) {
        return this.selectList(new LambdaQueryWrapper<AgentLog>()
                .eq(AgentLog::getUserId, userId)
                .ge(AgentLog::getCtTime, startTime)
                .lt(AgentLog::getCtTime, endTime)
                .orderByAsc(AgentLog::getCtTime));
    }

    /** 按事件类型聚合统计:指定时间区间内每个 event_type 的发生次数与去重用户数。 */
    List<Map<String, Object>> countByEventTypeAndDateRange(@Param("startTime") LocalDateTime startTime,
                                                           @Param("endTime") LocalDateTime endTime);
}
