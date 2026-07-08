package com.ink.speaker.common;

/**
 * 请求上下文(基于 ThreadLocal)。
 * <p>持有 novelId 与 userId,用于行级安全(R5 用户隔离)。</p>
 *
 * <p>填充入口:{@link com.ink.speaker.config.web.NovelContextFilter}。</p>
 * <p>读取入口:任意 Service / Tool 通过 {@link #getNovelId()} / {@link #getUserId()} 拿到当前请求归属。</p>
 *
 * <p><b>设计权衡:</b>相比给每个方法显式加 novelId/userId 参数,ThreadLocal 在 Controller/Tool
 * 这类"调用栈很深但末端统一需要"的场景下侵入更小。代价是异步线程需要手工透传,
 * 已通过 {@link com.ink.speaker.config.async.AsyncConfig#novelContextTaskDecorator} 解决。</p>
 *
 * <p><b>R5 用户隔离:</b></p>
 * <ul>
 *   <li>每本小说只对作者可见(ownerId 校验)</li>
 *   <li>跨小说参考时走公共 RAG 池(脱敏后),不暴露原作者信息</li>
 *   <li>userId 从 JWT subject 解析,novelId 从 X-Novel-Id 头解析</li>
 * </ul>
 */
public final class NovelContext {

    private static final ThreadLocal<Long> NOVEL_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private NovelContext() {
    }

    /** 设置当前请求的 novelId(由 Filter 调用)。 */
    public static void setNovelId(Long novelId) {
        NOVEL_ID.set(novelId);
    }

    /** 设置当前请求的 userId(由 Filter 从 JWT subject 解析后调用)。 */
    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    /**
     * 获取当前请求的 novelId。
     * <p>返回 null 表示请求未携带 novelId(如未鉴权用户、跨小说管理接口),
     * 调用方需自行处理回退或抛业务异常。</p>
     */
    public static Long getNovelId() {
        return NOVEL_ID.get();
    }

    /**
     * 获取当前请求的 userId(从 JWT subject 解析)。
     * <p>返回 null 表示未鉴权或解析失败。</p>
     */
    public static Long getUserId() {
        return USER_ID.get();
    }

    /**
     * 获取 novelId,若上下文为空则抛 400 业务异常。
     * <p>用于"必须知道是哪本小说"的强约束场景。</p>
     */
    public static Long requireNovelId() {
        Long id = NOVEL_ID.get();
        if (id == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "缺少 novelId:请通过请求头 X-Novel-Id 或 JWT claim 传入");
        }
        return id;
    }

    /**
     * 获取 userId,若上下文为空则抛 401 业务异常。
     * <p>用于"必须知道是谁在操作"的强约束场景(如章节保存、小说创建)。</p>
     */
    public static Long requireUserId() {
        Long id = USER_ID.get();
        if (id == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED,
                    "未鉴权:请先登录获取 access token");
        }
        return id;
    }

    /** 清理(由 Filter 在 finally 中调用,避免线程复用导致数据串联)。 */
    public static void clear() {
        NOVEL_ID.remove();
        USER_ID.remove();
    }
}
