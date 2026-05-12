package com.orderservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Published by: payment-service → payment-events topic
 * Consumed by:  order-service (to update status to PAYMENT_COMPLETED)
 *
 * Why transactionId is here: order-service needs to store it for audit/reconciliation.
 * If a dispute arises, ops can trace orderId → transactionId → payment gateway record.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent {
    private Long orderId;
    private Long userId;
    private String transactionId;
    private BigDecimal amount;
}
