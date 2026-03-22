package com.orderservice.exception;

/**
 * Thrown when an order is not found in the database.
 * Maps to HTTP 404 Not Found.
 */
public class OrderNotFoundException extends RuntimeException {
    
    public OrderNotFoundException(Long id) {
        super("Order not found with id: " + id);
    }
}
