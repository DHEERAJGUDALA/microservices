package com.notificationservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Notification-service's local copy of PaymentFailedEvent.
 * Received when payment fails → send "Order Cancelled (payment declined)" email.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedEvent {
    private Long orderId;
    private Long userId;
    private String reason;
}
