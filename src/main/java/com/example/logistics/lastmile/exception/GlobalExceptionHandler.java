package com.example.logistics.lastmile.exception;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.logistics.lastmile.common.Result;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== 业务异常（精确匹配 HTTP 状态码） ====================

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Result<String>> handleOrderNotFound(OrderNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Result.fail(e.getMessage()));
    }

    @ExceptionHandler(CourierNotFoundException.class)
    public ResponseEntity<Result<String>> handleCourierNotFound(CourierNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Result.fail(e.getMessage()));
    }

    @ExceptionHandler(CourierNotAvailableException.class)
    public ResponseEntity<Result<String>> handleCourierNotAvailable(CourierNotAvailableException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Result.fail(e.getMessage()));
    }

    @ExceptionHandler(IllegalStatusTransitionException.class)
    public ResponseEntity<Result<String>> handleIllegalStatusTransition(IllegalStatusTransitionException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.fail(e.getMessage()));
    }

    @ExceptionHandler(CourierLockedException.class)
    public ResponseEntity<Result<String>> handleCourierLocked(CourierLockedException e) {
        return ResponseEntity.status(HttpStatus.LOCKED).body(Result.fail(e.getMessage()));
    }

    // ==================== 兜底异常 ====================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<String>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors()
                .stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.fail(msg));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Result<String>> handleRuntimeException(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail("系统异常：" + e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<String>> handleException(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail("系统异常：" + e.getMessage()));
    }
}