package com.orderservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published by: inventory-service → inventory-events topic
 * Consumed by:  order-service (final success → CONFIRMED)
 *
 * Why productId + quantity are repeated here: The order-service doesn't need to re-fetch
 * from inventory to know what was reserved. The event is self-contained.
 * This is the "event carries all context" principle — consumers are stateless.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReservedEvent {
    private Long orderId;
    private Long productId;
    private Integer quantity;
}
