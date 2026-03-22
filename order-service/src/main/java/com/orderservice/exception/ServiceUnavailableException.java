package com.orderservice.exception;

/**
 * Thrown when a downstream service (user-service, product-service) is unavailable.
 * Maps to HTTP 503 Service Unavailable.
 */
public class ServiceUnavailableException extends RuntimeException {
    
    public ServiceUnavailableException(String serviceName) {
        super(serviceName + " is currently unavailable. Please try again later.");
    }
    
    public ServiceUnavailableException(String serviceName, Throwable cause) {
        super(serviceName + " is currently unavailable. Please try again later.", cause);
    }
}
