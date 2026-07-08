package com.ink.speaker.util;

import lombok.extern.slf4j.Slf4j;

/**
 * 控制器层公共工具:类型转换 / 字符串截断。
 * <p>抽取自原 NovelDataController,供各业务模块 Controller 复用。</p>
 */
@Slf4j
public final class ArgsUtil {

    private ArgsUtil() {
    }

    /**
     * 截断字符串到指定长度,超出部分以 "..." 标识。
     *
     * @param s   原始字符串(null 时返回空串)
     * @param max 最大长度
     * @return 截断后的字符串
     */
    public static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "...";
    }

    /**
     * 把任意对象转换为 Long,失败时返回默认值。
     */
    public static Long toLong(Object o, Long def) {
        if (o == null) {
            return def;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            log.warn("[ArgsUtil] toLong 解析失败: value={}, fallback={}", o, def);
            return def;
        }
    }

    /**
     * 把任意对象转换为 Integer,失败时返回默认值。
     */
    public static Integer toInt(Object o, Integer def) {
        if (o == null) {
            return def;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            log.warn("[ArgsUtil] toInt 解析失败: value={}, fallback={}", o, def);
            return def;
        }
    }

    /**
     * 提取异常根因,截断过长信息(最长 500 字符)。
     * <p>用于在 BusinessException message 中携带可读的根因描述。</p>
     */
    public static String reasonOf(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String reason = cur.getMessage();
        if (reason == null || reason.isBlank()) {
            reason = cur.getClass().getSimpleName();
        }
        if (reason.length() > 500) {
            reason = reason.substring(0, 500) + "...";
        }
        return reason;
    }

    /**
     * 截取文本尾部 N 字符,前置加 "..." 表示省略。
     * <p>用于续生 prompt 的"前情提要"。</p>
     */
    public static String truncateTail(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return "..." + text.substring(text.length() - maxChars);
    }
}
