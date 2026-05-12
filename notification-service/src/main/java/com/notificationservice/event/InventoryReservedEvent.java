package com.notificationservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Notification-service's local copy of InventoryReservedEvent.
 * Received when the full Saga succeeds → send "Order Confirmed!" email.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReservedEvent {
    private Long orderId;
    private Long productId;
    private Integer quantity;
}
