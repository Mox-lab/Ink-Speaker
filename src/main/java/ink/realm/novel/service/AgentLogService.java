package ink.realm.novel.service;

import ink.realm.novel.domain.entity.AgentLog;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 漏斗埋点服务接口(UX-11)。
 */
public interface AgentLogService {

    /**
     * 上报一条事件。
     *
     * @param eventType 事件类型,例如 funnel.login
     * @param props     附加属性,允许为 null
     * @param userId    用户 id,允许为 null(未登录)
     * @param novelId   小说 id,允许为 null(全局事件)
     */
    void track(String eventType, Map<String, Object> props, Long userId, Long novelId);

    /**
     * 按时间区间统计 event_type 聚合数据,供漏斗分析使用。
     *
     * @return 每行:{ eventType, total, uniqUsers }
     */
    List<Map<String, Object>> aggregateByEventType(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 查询指定用户在时间区间内的事件轨迹(漏斗路径回放)。
     */
    List<AgentLog> listUserTrace(Long userId, LocalDateTime startTime, LocalDateTime endTime);
}
