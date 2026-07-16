package ink.realm.novel.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import ink.realm.novel.domain.entity.AgentLog;
import ink.realm.novel.mapper.AgentLogMapper;
import ink.realm.novel.service.AgentLogService;
import ink.realm.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 漏斗埋点服务实现(UX-11)。
 * <p>所有上报走异步路径,失败仅记日志,不影响主业务。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentLogServiceImpl implements AgentLogService {

    private final AgentLogMapper agentLogDao;

    @Override
    public void track(String eventType, Map<String, Object> props, Long userId, Long novelId) {
        if (eventType == null || eventType.isBlank()) {
            log.warn("[AgentLog] track called with blank eventType, ignored");
            return;
        }
        try {
            AgentLog entity = new AgentLog();
            entity.setUserId(userId);
            entity.setNovelId(novelId);
            entity.setEventType(eventType);
            entity.setProps(serializeProps(props));
            agentLogDao.insert(entity);
        } catch (Exception e) {
            // 埋点失败不应中断主流程,仅记录警告
            log.warn("[AgentLog] track failed for eventType={}: {}", eventType, e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> aggregateByEventType(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
            return List.of();
        }
        return agentLogDao.countByEventTypeAndDateRange(startTime, endTime);
    }

    @Override
    public List<AgentLog> listUserTrace(Long userId, LocalDateTime startTime, LocalDateTime endTime) {
        if (userId == null || startTime == null || endTime == null) {
            return List.of();
        }
        return agentLogDao.listByUserIdAndDateRange(userId, startTime, endTime);
    }

    private String serializeProps(Map<String, Object> props) {
        if (props == null || props.isEmpty()) {
            return null;
        }
        try {
            return JsonUtil.MAPPER.writeValueAsString(props);
        } catch (JsonProcessingException e) {
            log.warn("[AgentLog] serialize props failed: {}", e.getMessage());
            return null;
        }
    }
}
