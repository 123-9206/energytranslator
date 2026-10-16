package com.energy.translator.exception;

import com.energy.translator.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一异常处理：将 Controller/Service 抛出的异常转换为标准 ApiResponse(JSON)，
 * 避免裸抛 500 + 堆栈 HTML（既泄露信息，又破坏前端约定的 {success,data,error,message} 结构）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务运行时异常：返回 200 + success=false + error=消息（前端按业务错误处理） */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException ex) {
        log.error("业务运行时异常: {}", ex.getMessage(), ex);
        String msg = ex.getMessage() != null ? ex.getMessage() : "业务处理失败";
        return ResponseEntity.ok(ApiResponse.error(msg));
    }

    /** 参数校验失败（@Valid / @NotBlank 等）：提取第一条错误信息 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getDefaultMessage())
                .orElse("参数校验失败");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(msg));
    }

    /** 权限不足：转换为 403 + success=false */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("无权访问该资源"));
    }

    /** 兜底：其它未预期异常，不泄露堆栈，统一 500 + 通用文案（仍记录日志便于排障） */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        log.error("未预期异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("服务器内部错误，请稍后重试"));
    }
}
