package com.inventoryservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Published by: payment-service → payment-events topic
 * Consumed by:  inventory-service (triggers stock reservation)
 *
 * Inventory-service's LOCAL COPY of PaymentCompletedEvent.
 * productId and quantity are carried here so inventory-service
 * knows exactly which product to reserve and how many units.
 *
 * Senior note: This demonstrates the "smart consumer, dumb broker" principle.
 * The Kafka broker just stores bytes — it doesn't route based on content.
 * The consumer decides what to do based on the event type it receives.
 * inventory-service subscribes to payment-events and only acts on PaymentCompletedEvent.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent {
    private Long orderId;
    private Long userId;
    private Long productId;
    private Integer quantity;
    private String transactionId;
    private BigDecimal amount;
}
