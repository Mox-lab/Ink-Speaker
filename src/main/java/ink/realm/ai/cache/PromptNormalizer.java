package ink.realm.ai.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * LLM Prompt 规范化工具:在调用 @Cacheable 之前清洗输入,提高缓存命中率。
 *
 * <p>背景:LLM 缓存 key 通常基于原始入参的 hashCode。如果用户输入有尾部空白、
 * 大小写差异、CRLF 换行不一致等,会导致本可命中的请求落空。本工具统一做:</p>
 * <ol>
 *   <li>去除首尾空白</li>
 *   <li>统一换行符为 {@code \n}</li>
 *   <li>合并连续空白(避免多余空格干扰)</li>
 *   <li>空值统一为空串(避免 null vs "" 在 key 中差异)</li>
 * </ol>
 *
 * <p>使用:</p>
 * <pre>
 * &#64;Cacheable(value = "llmConcept", key = "T(ink.realm.ai.cache.PromptNormalizer).stableKey(#inspiration, #genre)")
 * String expand(String inspiration, String genre);
 * </pre>
 *
 * <p>或在 Controller 中先规范化再调用:</p>
 * <pre>
 * String norm = promptNormalizer.normalize(raw);
 * String result = conceptAgent.expand(norm, genre);
 * </pre>
 */
@Slf4j
@Component
public class PromptNormalizer {

    /** 用于规范化后产稳定 hash key 的算法。 */
    public static final String HASH_ALGORITHM = "SHA-256";

    /** hash 输出固定 64 字符 hex。 */
    public static final int HASH_HEX_LENGTH = 64;

    /**
     * 规范化 prompt 文本。
     *
     * @param raw 原始文本(null → 空串)
     * @return 规范化后文本(never null)
     */
    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        // 统一换行 \r\n / \r → \n
        String s = raw.replace("\r\n", "\n").replace("\r", "\n");
        // 去首尾空白
        s = s.strip();
        // 合并连续空白(但保留换行符):先把行内多空格压成单空格
        StringBuilder sb = new StringBuilder(s.length());
        boolean lastWasSpace = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t') {
                if (!lastWasSpace) {
                    sb.append(' ');
                }
                lastWasSpace = true;
            } else {
                sb.append(c);
                lastWasSpace = false;
            }
        }
        return sb.toString();
    }

    /**
     * 多参数合并规范化后求 SHA-256,作为稳定的 cache key。
     *
     * <p>用法:在 @Cacheable 的 key 表达式中,通过 SpEL 调用本方法,
     * 把多个入参合并为一个稳定 hash,避免参数顺序差异导致 key 不一致。</p>
     *
     * @param parts 多个 prompt 组成部分
     * @return 64 字符 hex 串(空输入返回 "empty" 占位)
     */
    public String stableKey(String... parts) {
        if (parts == null || parts.length == 0) {
            return "empty";
        }
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                joined.append('\u0001');
            }
            joined.append(normalize(parts[i]));
        }
        try {
            MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] digest = md.digest(joined.toString().getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (NoSuchAlgorithmException e) {
            log.warn("[PromptNormalizer] {} not available, fallback to hashCode", HASH_ALGORITHM);
            return Integer.toHexString(joined.hashCode());
        }
    }

    /**
     * 字节数组转 hex 小写串。
     */
    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
