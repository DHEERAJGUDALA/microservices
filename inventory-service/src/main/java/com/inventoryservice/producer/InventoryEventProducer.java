package com.inventoryservice.producer;

import com.inventoryservice.event.InventoryFailedEvent;
import com.inventoryservice.event.InventoryReservedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Centralizes all Kafka publishing for inventory-service.
 *
 * Published to: inventory-events topic
 *
 * Consumers of this topic:
 * - order-service (payment-group-like listener) → to finalize order status
 * - payment-service (for InventoryFailedEvent) → to trigger refund
 * - notification-service → to send confirmation/failure email
 *
 * This fan-out to multiple consumers happens automatically via Kafka consumer groups.
 * inventory-service doesn't know how many consumers are listening —
 * it just publishes. This is loose coupling at its best.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.kafka.topics.inventory-events:inventory-events}")
    private String inventoryEventsTopic;

    /**
     * Publishes success event. Triggers:
     * - order-service: update order to CONFIRMED
     * - notification-service: send "Your order is confirmed!" email
     */
    public void publishInventoryReserved(InventoryReservedEvent event) {
        log.info("Publishing InventoryReservedEvent for orderId={}, productId={}, qty={}",
                event.getOrderId(), event.getProductId(), event.getQuantity());

        kafkaTemplate.send(inventoryEventsTopic, event.getOrderId().toString(), event);

        log.info("InventoryReservedEvent published successfully for orderId={}", event.getOrderId());
    }

    /**
     * Publishes failure event. Triggers:
     * - order-service: update order to CANCELLED
     * - payment-service: issue refund (compensating transaction)
     * - notification-service: send "Order cancelled — insufficient stock" email
     */
    public void publishInventoryFailed(InventoryFailedEvent event) {
        log.error("Publishing InventoryFailedEvent for orderId={}, productId={}, reason={}",
                event.getOrderId(), event.getProductId(), event.getReason());

        kafkaTemplate.send(inventoryEventsTopic, event.getOrderId().toString(), event);

        log.info("InventoryFailedEvent published — compensating transactions will be triggered for orderId={}",
                event.getOrderId());
    }
}
