package com.inventoryservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published by: inventory-service → inventory-events topic
 * Consumed by:  order-service (marks order CANCELLED)
 *              payment-service (triggers refund — compensating transaction)
 *
 * This is the FAILURE terminal event of the Saga when inventory fails.
 * Two independent consumers react to it without knowing about each other.
 * This is the beauty of choreography-based Saga over orchestration:
 * no central controller, each service reacts autonomously to events it cares about.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryFailedEvent {
    private Long orderId;
    private Long productId;
    private String reason;
}
