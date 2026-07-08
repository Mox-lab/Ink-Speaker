package com.ink.speaker.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务状态码枚举。
 * <p>约定:1xxx 通用业务错误,2xxx 鉴权,4xxx 客户端,5xxx 服务端。</p>
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    /** 成功。 */
    SUCCESS(200, "OK"),

    /** 通用业务错误(参数不合法、状态不允许等)。 */
    BUSINESS_ERROR(1000, "业务异常"),

    /** 参数校验失败。 */
    PARAM_INVALID(1001, "参数校验失败"),

    /** 资源不存在。 */
    NOT_FOUND(1002, "资源不存在"),

    /** 资源已存在(冲突)。 */
    CONFLICT(1003, "资源已存在"),

    /** 未认证。 */
    UNAUTHORIZED(2001, "未认证"),

    /** 无权限。 */
    FORBIDDEN(2003, "无权限"),

    /** 通用客户端错误。 */
    BAD_REQUEST(4000, "请求错误"),

    /** 系统内部错误。 */
    SYSTEM_ERROR(5000, "系统内部错误");

    /** 状态码。 */
    private final int code;

    /** 默认提示。 */
    private final String message;
}
