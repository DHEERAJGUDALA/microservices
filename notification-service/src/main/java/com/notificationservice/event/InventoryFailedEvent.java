package com.notificationservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Notification-service's local copy of InventoryFailedEvent.
 * Received when stock reservation fails → send "Order Cancelled (out of stock)" email.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryFailedEvent {
    private Long orderId;
    private Long productId;
    private String reason;
}
