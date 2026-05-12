package com.orderservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published by: payment-service → payment-events topic
 * Consumed by:  order-service (to update status to PAYMENT_FAILED → CANCELLED)
 *
 * Why reason field: For customer-facing error messages and for ops debugging.
 * "Payment gateway declined" vs "Internal error" vs "Duplicate transaction" all need different handling.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedEvent {
    private Long orderId;
    private Long userId;
    private String reason;
}
