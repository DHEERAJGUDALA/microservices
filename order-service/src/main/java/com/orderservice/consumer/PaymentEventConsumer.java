package com.orderservice.consumer;

import com.orderservice.entity.Order;
import com.orderservice.event.PaymentCompletedEvent;
import com.orderservice.event.PaymentFailedEvent;
import com.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order-service Saga Status Updater — Payment Events
 *
 * Listens to: payment-events
 * Action: Update order status based on payment outcome
 *
 * WHY order-service listens instead of having payment-service call order-service?
 * Because that would be SYNCHRONOUS coupling — payment-service would depend on
 * order-service being up. If order-service is down, payment-service can't complete.
 * With event-driven architecture, payment-service publishes and forgets.
 * Order-service updates itself whenever it's available — total decoupling.
 *
 * WHY does order-service update its own DB directly (via repository) instead of
 * calling its own service method?
 * Performance + simplicity. The consumer only needs to change status.
 * Calling OrderService.updateOrderStatus() would trigger Feign calls to
 * user-service and product-service just to build an OrderResponse — waste of resources.
 * Direct repository access here is intentional and correct.
 *
 * groupId = "order-saga-group"
 * Separate from any other group. The order-service is the only consumer in this group
 * for this topic, so it gets every message.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final OrderRepository orderRepository;

    @KafkaListener(
            topics = "payment-events",
            groupId = "order-saga-group",
            containerFactory = "orderKafkaListenerContainerFactory"
    )
    @Transactional
    public void handlePaymentEvent(Object rawEvent) {
        // Spring's JsonDeserializer with type mapping routes to the correct type.
        // We use overloaded methods for clean separation.
        if (rawEvent instanceof PaymentCompletedEvent event) {
            handlePaymentCompleted(event);
        } else if (rawEvent instanceof PaymentFailedEvent event) {
            handlePaymentFailed(event);
        } else {
            log.debug("OrderService: Ignoring unknown payment event type: {}",
                    rawEvent.getClass().getSimpleName());
        }
    }

    private void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("OrderService: Payment completed for orderId={}", event.getOrderId());
        updateOrderStatus(event.getOrderId(), Order.OrderStatus.PAYMENT_COMPLETED);
    }

    private void handlePaymentFailed(PaymentFailedEvent event) {
        log.warn("OrderService: Payment failed for orderId={}, reason={}", event.getOrderId(), event.getReason());
        updateOrderStatus(event.getOrderId(), Order.OrderStatus.CANCELLED);
    }

    private void updateOrderStatus(Long orderId, Order.OrderStatus newStatus) {
        orderRepository.findById(orderId).ifPresentOrElse(order -> {
            order.setStatus(newStatus);
            orderRepository.save(order);
            log.info("Order {} status updated to {}", orderId, newStatus);
        }, () -> log.error("OrderService: Order {} not found — cannot update status to {}",
                orderId, newStatus));
    }
}
