package com.paymentservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Published by: order-service → order-events topic
 * Consumed by:  payment-service (triggers payment processing)
 *              notification-service (sends "order received" notification)
 *
 * This is payment-service's LOCAL COPY of the event.
 * It has the same fields as order-service's OrderCreatedEvent — intentional duplication.
 *
 * Senior note: totalPrice is here so payment-service doesn't need to call
 * product-service to know what to charge. The event is self-contained.
 * Always design events to contain everything the consumer needs — avoid
 * "event-carried state transfer" anti-pattern where consumers call back to fetch data.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {
    private Long orderId;
    private Long userId;
    private Long productId;
    private Integer quantity;
    private BigDecimal totalPrice;
    private LocalDateTime createdAt;
}
