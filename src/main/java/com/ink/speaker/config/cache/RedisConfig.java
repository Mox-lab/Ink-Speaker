package com.ink.speaker.config.cache;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.nio.charset.StandardCharsets;

/**
 * Redis 配置(L2 缓存后端 + 分布式锁)。
 *
 * <p>第 8 阶段(L1+L2 多级缓存):提供 L2 Redis Template,
 * 与 Caffeine L1 配合使用,见 {@link MultiLevelCache}。</p>
 *
 * <p>序列化策略:</p>
 * <ul>
 *   <li>key:StringRedisSerializer(LLM 缓存 key 已是 SHA-256 hex,无需再序列化)</li>
 *   <li>value:自定义 ByteArrayRedisSerializer,直接存 UTF-8 字节,
 *       反序列化回 String(规避 Spring Boot 4.x 中已过时的 GenericJackson2JsonRedisSerializer)</li>
 * </ul>
 *
 * <p>注意:此 Bean 仅用于 {@link MultiLevelCache} 显式注入,
 * 不与 Spring Cache 抽象( {@code @Cacheable} )共用,避免序列化差异导致兼容问题。</p>
 */
@Configuration
public class RedisConfig {

    /**
     * 自定义 RedisTemplate:key 用 String,value 用 UTF-8 字节。
     *
     * <p>value 不走 JSON,因为 {@link MultiLevelCache} 存的就是 String(LLM 响应)。
     * JSON 序列化反而会引入转义和类型信息开销。</p>
     *
     * @param factory Redis 连接工厂(由 spring-boot-starter-data-redis 自动装配)
     * @return RedisTemplate 实例
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // key 用 String 序列化
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // value 用 UTF-8 字节直存,避免 GenericJackson2JsonRedisSerializer 过时 API 警告
        template.setValueSerializer(new StringBytesRedisSerializer());
        template.setHashValueSerializer(new StringBytesRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }

    /**
     * String ↔ UTF-8 字节 序列化器。
     * <p>比 {@code GenericJackson2JsonRedisSerializer}(Spring Boot 4.x 已过时)更轻量,
     * 适合 MultiLevelCache 仅存 String 类型的场景。</p>
     */
    private static class StringBytesRedisSerializer
            implements org.springframework.data.redis.serializer.RedisSerializer<Object> {

        @Override
        public byte[] serialize(Object t) {
            if (t == null) {
                return null;
            }
            return t.toString().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public Object deserialize(byte[] bytes) {
            if (bytes == null) {
                return null;
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
