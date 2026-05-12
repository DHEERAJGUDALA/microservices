package com.productservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global Exception Handler — consistent error responses across all endpoints.
 *
 * @RestControllerAdvice: intercepts exceptions from ALL @RestController classes.
 * Without this, Spring returns its default HTML error page or generic JSON — unusable by API clients.
 *
 * WHY centralized here instead of try-catch in every controller?
 * - Single place to change error format (JSON field names, HTTP codes)
 * - Consistent structure: every error follows the same schema
 * - Controllers stay clean — only happy-path code
 *
 * STRUCTURED ERROR RESPONSE:
 * {
 *   "timestamp": "2024-01-15T10:30:00",
 *   "status": 404,
 *   "error": "Product not found: 999"
 * }
 *
 * VALIDATION ERROR RESPONSE (when @Valid fails):
 * {
 *   "timestamp": "2024-01-15T10:30:00",
 *   "status": 400,
 *   "errors": {
 *     "name": "Product name is required",
 *     "price": "Price must be greater than 0"
 *   }
 * }
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles product not found (RuntimeException from ProductService).
     * In production, you'd create a custom ProductNotFoundException extends RuntimeException.
     * For simplicity here, we handle the generic RuntimeException.
     * Returns HTTP 404 NOT FOUND.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        log.error("RuntimeException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                errorBody(HttpStatus.NOT_FOUND.value(), ex.getMessage())
        );
    }

    /**
     * Handles @Valid validation failures on request body.
     * MethodArgumentNotValidException is thrown by Spring when @Valid fails.
     * We collect all field-level errors into a map and return HTTP 400.
     *
     * Without this handler: Spring returns a generic 400 with a massive stack trace.
     * With this handler: clean response like {"name": "Product name is required"}
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        log.warn("Validation failed: {}", fieldErrors);

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("errors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    private Map<String, Object> errorBody(int status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status);
        body.put("error", message);
        return body;
    }
}
