package com.ink.speaker.common;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。
 * <p>统一拦截 Controller 抛出的异常,转换为 {@link Result} 响应。</p>
 *
 * <p>处理优先级:</p>
 * <ol>
 *   <li>{@link BusinessException} — 业务异常,带原始 code</li>
 *   <li>{@link MethodArgumentNotValidException} — @Valid 校验失败</li>
 *   <li>{@link ConstraintViolationException} — @Validated 校验失败</li>
 *   <li>{@link IllegalArgumentException} — 参数非法</li>
 *   <li>{@link Throwable} — 兜底:统一为 SYSTEM_ERROR</li>
 * </ol>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常:保留原始 code 与 message。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException ex) {
        log.warn("[BusinessException] code={}, msg={}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.OK)
                .body(Result.fail(ex.getCode(), ex.getMessage()));
    }

    /**
     * @Valid 校验失败:POST body 字段不合法。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleNotValid(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("[NotValid] {}", msg);
        return ResponseEntity.status(HttpStatus.OK)
                .body(Result.fail(ResultCode.PARAM_INVALID, msg));
    }

    /**
     * 表单绑定失败。
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBind(BindException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("[BindException] {}", msg);
        return ResponseEntity.status(HttpStatus.OK)
                .body(Result.fail(ResultCode.PARAM_INVALID, msg));
    }

    /**
     * @Validated 路径/查询参数校验失败。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("[ConstraintViolation] {}", msg);
        return ResponseEntity.status(HttpStatus.OK)
                .body(Result.fail(ResultCode.PARAM_INVALID, msg));
    }

    /**
     * 参数类型不匹配(如路径变量无法转 Long)。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String msg = "参数 " + ex.getName() + " 类型不合法";
        log.warn("[TypeMismatch] {} value={}", ex.getName(), ex.getValue());
        return ResponseEntity.status(HttpStatus.OK)
                .body(Result.fail(ResultCode.PARAM_INVALID, msg));
    }

    /**
     * 非法参数:兜底转为 PARAM_INVALID。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleIllegalArg(IllegalArgumentException ex) {
        log.warn("[IllegalArgument] {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.OK)
                .body(Result.fail(ResultCode.PARAM_INVALID, ex.getMessage()));
    }

    /**
     * 兜底:未捕获的异常。
     */
    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Result<Void>> handleThrowable(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        log.error("[Uncatched] {}: {}", root.getClass().getSimpleName(), root.getMessage(), ex);
        String msg = "系统繁忙,请稍后重试";
        return ResponseEntity.status(HttpStatus.OK)
                .body(Result.fail(ResultCode.SYSTEM_ERROR, msg));
    }

    private String formatFieldError(FieldError fe) {
        return fe.getField() + ":" + fe.getDefaultMessage();
    }
}
