package com.orderservice.exception;

import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Global exception handler for the order-service.
 * Converts exceptions to proper HTTP responses with consistent error format.
 * 
 * Why @RestControllerAdvice?
 * - Catches exceptions from ALL controllers in one place
 * - Returns JSON responses (not HTML error pages)
 * - Single place to change error format
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles OrderNotFoundException -> 404 Not Found
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(
            OrderNotFoundException ex, HttpServletRequest request) {
        
        log.warn("Order not found: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.NOT_FOUND.value())
                        .error("Not Found")
                        .message(ex.getMessage())
                        .path(request.getRequestURI())
                        .build());
    }

    /**
     * Handles ResourceNotFoundException (User/Product not found) -> 404 Not Found
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        
        log.warn("Resource not found: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.NOT_FOUND.value())
                        .error("Not Found")
                        .message(ex.getMessage())
                        .path(request.getRequestURI())
                        .build());
    }

    /**
     * Handles ServiceUnavailableException -> 503 Service Unavailable
     */
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleServiceUnavailable(
            ServiceUnavailableException ex, HttpServletRequest request) {
        
        log.error("Service unavailable: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                        .error("Service Unavailable")
                        .message(ex.getMessage())
                        .path(request.getRequestURI())
                        .build());
    }

    /**
     * Handles Feign client errors (when calling user-service or product-service fails)
     * 
     * FeignException.status() tells us what the downstream service returned:
     * - 404 from user-service -> User not found
     * - 500 from user-service -> Service error
     * - Connection refused -> Service down
     */
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ErrorResponse> handleFeignException(
            FeignException ex, HttpServletRequest request) {
        
        log.error("Feign client error: status={}, message={}", ex.status(), ex.getMessage());
        
        HttpStatus status;
        String message;
        
        if (ex.status() == 404) {
            // Downstream service returned 404 - resource not found
            status = HttpStatus.NOT_FOUND;
            message = "Requested resource not found in upstream service";
        } else if (ex.status() == -1) {
            // Connection refused - service is down
            status = HttpStatus.SERVICE_UNAVAILABLE;
            message = "Upstream service is currently unavailable";
        } else if (ex.status() >= 500) {
            // Downstream service error
            status = HttpStatus.BAD_GATEWAY;
            message = "Error from upstream service";
        } else {
            // Other client errors
            status = HttpStatus.BAD_REQUEST;
            message = "Invalid request to upstream service";
        }
        
        return ResponseEntity.status(status)
                .body(ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(status.value())
                        .error(status.getReasonPhrase())
                        .message(message)
                        .path(request.getRequestURI())
                        .build());
    }

    /**
     * Handles validation errors from @Valid -> 400 Bad Request
     * 
     * Example: If OrderRequest has @NotNull on userId and client sends null,
     * this handler formats the error nicely.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        
        // Collect all validation errors into a single message
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        
        log.warn("Validation failed: {}", errors);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error("Validation Failed")
                        .message(errors)
                        .path(request.getRequestURI())
                        .build());
    }

    /**
     * Catch-all for any unhandled exceptions -> 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllOtherExceptions(
            Exception ex, HttpServletRequest request) {
        
        log.error("Unexpected error: ", ex);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .error("Internal Server Error")
                        .message("An unexpected error occurred")
                        .path(request.getRequestURI())
                        .build());
    }
}
