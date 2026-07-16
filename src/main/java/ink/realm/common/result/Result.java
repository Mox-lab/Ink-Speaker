package ink.realm.common.result;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一响应体。
 * <p>所有 Controller 返回统一封装:{@code code, message, data}。</p>
 *
 * <p>用法:</p>
 * <pre>{@code
 *   return Result.success(data);
 *   return Result.fail(ResultCode.BUSINESS_ERROR, "章节正文不能为空");
 * }</pre>
 *
 * <p><b>序列化说明:</b>HTTP 接口走 Jackson(JSON),不依赖 Java 原生序列化。
 * {@link Serializable} 仅作为 Spring 缓存(Redis/JDK 序列化器)场景的兜底契约,
 * 故 data 字段标记为 {@code transient} —— 若未来引入 JDK 序列化器,序列化时跳过业务数据,
 * 反序列化后需重新从源头加载(避免不可序列化类型抛 NotSerializableException)。</p>
 *
 * @param <T> data 字段类型
 */
@Data
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务状态码:200 成功;4xx 客户端错误;5xx 服务端错误。 */
    private int code;

    /** 提示信息(成功为 "OK",失败为错误描述)。 */
    private String message;

    /** 业务数据(Jackson JSON 序列化正常工作;Java 原生序列化时跳过)。 */
    private transient T data;

    public Result() {
    }

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 构造成功响应(无 data)。
     */
    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    /**
     * 构造成功响应(带 data)。
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /**
     * 构造失败响应(基于 ResultCode 枚举)。
     */
    public static <T> Result<T> fail(ResultCode code) {
        return new Result<>(code.getCode(), code.getMessage(), null);
    }

    /**
     * 构造失败响应(基于 ResultCode 枚举 + 自定义提示)。
     */
    public static <T> Result<T> fail(ResultCode code, String message) {
        return new Result<>(code.getCode(), message != null ? message : code.getMessage(), null);
    }

    /**
     * 构造失败响应(原始 code + 提示)。
     */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
