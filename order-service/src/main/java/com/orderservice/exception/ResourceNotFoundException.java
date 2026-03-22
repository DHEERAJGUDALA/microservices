package com.orderservice.exception;

/**
 * Thrown when a resource (User, Product) is not found in the upstream service.
 * Maps to HTTP 404 Not Found.
 */
public class ResourceNotFoundException extends RuntimeException {
    
    public ResourceNotFoundException(String resourceType, Long id) {
        super(resourceType + " not found with id: " + id);
    }
}
