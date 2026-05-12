package com.inventoryservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published by: inventory-service → inventory-events topic
 * Consumed by:  order-service (marks order CONFIRMED)
 *              notification-service (sends "order confirmed" email)
 *
 * This is the SUCCESS terminal event of the entire Saga.
 * When this lands, the full happy path is complete:
 * Order created → Payment processed → Stock reserved → Order CONFIRMED
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReservedEvent {
    private Long orderId;
    private Long productId;
    private Integer quantity;
}
