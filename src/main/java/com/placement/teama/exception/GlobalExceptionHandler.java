package com.placement.teama.exception;

import com.placement.teama.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import com.placement.teama.service.IdempotencyConflictException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        String corrId = request.getHeader("X-Correlation-ID");
        if (corrId == null) corrId = "corr-err-400";
        String message = ex.getMessage() == null ? "Invalid request" : ex.getMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_ARGUMENT", message, corrId,
                        java.util.List.<Object>of(java.util.Map.of("reason", message))));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                                  HttpServletRequest request) {
        String corrId = request.getHeader("X-Correlation-ID");
        if (corrId == null) corrId = "corr-err-400";
        String message = "Request body contains malformed JSON or values incompatible with the API schema";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_REQUEST_BODY", message, corrId,
                        java.util.List.<Object>of(java.util.Map.of("reason", message))));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleIdempotencyConflict(IdempotencyConflictException ex, HttpServletRequest request) {
        String corrId = request.getHeader("X-Correlation-ID");
        if (corrId == null) corrId = "corr-err-409";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error("IDEMPOTENCY_CONFLICT", ex.getMessage(), corrId));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String corrId = request.getHeader("X-Correlation-ID");
        if (corrId == null) corrId = "corr-err-400";
        String message = ex.getBindingResult().getFieldErrors().isEmpty() ? "Invalid request" : ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("VALIDATION_FAILED", message, corrId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex, HttpServletRequest request) {
        String corrId = request.getHeader("X-Correlation-ID");
        if (corrId == null) corrId = "corr-err-500";
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_SERVER_ERROR", ex.getMessage(), corrId));
    }
}
