package com.ink.speaker.common;

import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常。
 * <p>Service 层抛出后,由 {@code GlobalExceptionHandler} 统一捕获并转 Result 响应。</p>
 *
 * <p>用法:</p>
 * <pre>{@code
 *   throw new BusinessException(ResultCode.PARAM_INVALID, "content 不能为空");
 *   throw new BusinessException("章节生成失败");
 * }</pre>
 */
@Getter
public class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务状态码(默认 BUSINESS_ERROR)。 */
    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.BUSINESS_ERROR.getCode();
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message != null ? message : resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
