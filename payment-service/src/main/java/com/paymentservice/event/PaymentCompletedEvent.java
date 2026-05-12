package com.paymentservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Published by: payment-service → payment-events topic
 * Consumed by:  inventory-service (triggers stock reservation)
 *              order-service (updates status to PAYMENT_COMPLETED)
 *
 * Why transactionId is carried forward: Inventory-service will include it in
 * InventoryReservedEvent so the final order confirmation has a full audit trail:
 * orderId → transactionId → payment record → stock reservation record
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
