package com.ink.speaker.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Map;

/**
 * JSON 公共工具:共享 {@link ObjectMapper} 实例与通用反序列化方法。
 * <p>阿里规范:工具类需为 final + 私有构造,共享昂贵资源(ObjectMapper)以避免重复创建。</p>
 */
@Slf4j
public final class JsonUtil {

    /** 共享 ObjectMapper(线程安全)。 */
    public static final ObjectMapper MAPPER = new ObjectMapper();

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private JsonUtil() {
    }

    /**
     * 把 JSON 字符串解析为 Map。
     * <p>解析失败时返回空 Map(不抛异常),并记录 warn 日志。</p>
     *
     * @param json 原始 JSON 字符串
     * @return 解析后的 Map;入参为空或失败时返回 {@link Collections#emptyMap()}
     */
    public static Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            log.warn("[JsonUtil] 解析 JSON 为 Map 失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
