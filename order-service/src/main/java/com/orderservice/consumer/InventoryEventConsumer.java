package com.orderservice.consumer;

import com.orderservice.entity.Order;
import com.orderservice.event.InventoryFailedEvent;
import com.orderservice.event.InventoryReservedEvent;
import com.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order-service Saga Status Updater — Inventory Events (Final State)
 *
 * Listens to: inventory-events
 * Action: Set the FINAL order state
 *   - InventoryReservedEvent → CONFIRMED (Saga SUCCESS)
 *   - InventoryFailedEvent   → CANCELLED (Saga FAILURE)
 *
 * This is the TERMINAL step of the Saga for order-service.
 * After this, the order is either in CONFIRMED or CANCELLED state.
 * CONFIRMED orders then move through SHIPPED → DELIVERED via manual/admin action.
 *
 * CONCURRENT EVENT SAFETY:
 * What if InventoryReservedEvent and InventoryFailedEvent somehow both arrive
 * for the same orderId? (shouldn't happen but defensive programming matters)
 * The @Transactional on each listener method ensures only one update runs at a time.
 * In practice, inventory-service publishes EXACTLY ONE of these per order.
 *
 * SAME groupId as PaymentEventConsumer ("order-saga-group"):
 * Both these consumers are in the same group. They listen to DIFFERENT topics.
 * groupId is scoped per topic — "order-saga-group" on payment-events is a
 * separate subscription from "order-saga-group" on inventory-events.
 * They don't interfere. The naming just conveys they're both part of the order's saga handling.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryEventConsumer {

    private final OrderRepository orderRepository;

    @KafkaListener(
            topics = "inventory-events",
            groupId = "order-saga-group",
            containerFactory = "orderKafkaListenerContainerFactory"
    )
    @Transactional
    public void handleInventoryEvent(Object rawEvent) {
        if (rawEvent instanceof InventoryReservedEvent event) {
            handleInventoryReserved(event);
        } else if (rawEvent instanceof InventoryFailedEvent event) {
            handleInventoryFailed(event);
        } else {
            log.debug("OrderService: Ignoring unknown inventory event type: {}",
                    rawEvent.getClass().getSimpleName());
        }
    }

    private void handleInventoryReserved(InventoryReservedEvent event) {
        log.info("OrderService: Inventory reserved for orderId={}. Setting to CONFIRMED.", event.getOrderId());
        updateOrderStatus(event.getOrderId(), Order.OrderStatus.CONFIRMED);
    }

    private void handleInventoryFailed(InventoryFailedEvent event) {
        log.warn("OrderService: Inventory reservation failed for orderId={}, reason={}. Setting to CANCELLED.",
                event.getOrderId(), event.getReason());
        updateOrderStatus(event.getOrderId(), Order.OrderStatus.CANCELLED);
    }

    private void updateOrderStatus(Long orderId, Order.OrderStatus newStatus) {
        orderRepository.findById(orderId).ifPresentOrElse(order -> {
            order.setStatus(newStatus);
            orderRepository.save(order);
            log.info("Order {} final status set to {}", orderId, newStatus);
        }, () -> log.error("OrderService: Order {} not found — cannot set final status to {}",
                orderId, newStatus));
    }
}
