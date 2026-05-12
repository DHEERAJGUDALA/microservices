package com.paymentservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published by: payment-service → payment-events topic
 * Consumed by:  order-service (updates status to PAYMENT_FAILED → CANCELLED)
 *
 * Note: No inventory compensation needed here because inventory reservation
 * never started — payment failed BEFORE inventory step in the saga flow.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedEvent {
    private Long orderId;
    private Long userId;
    private String reason;
}
