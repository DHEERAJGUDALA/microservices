package com.orderservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published by: inventory-service → inventory-events topic
 * Consumed by:  order-service (final failure → CANCELLED)
 *              payment-service (triggers refund — compensating transaction)
 *
 * Senior note: This single event is consumed by TWO services.
 * That is the power of Kafka topics over point-to-point queues.
 * payment-service listens on inventory-events for THIS type and auto-refunds.
 * order-service listens on inventory-events for THIS type and cancels the order.
 * They act independently — no coordination needed.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryFailedEvent {
    private Long orderId;
    private Long productId;
    private String reason;
}
