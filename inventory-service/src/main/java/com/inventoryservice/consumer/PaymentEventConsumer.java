package com.inventoryservice.consumer;

import com.inventoryservice.dto.StockReservationRequest;
import com.inventoryservice.event.InventoryFailedEvent;
import com.inventoryservice.event.InventoryReservedEvent;
import com.inventoryservice.event.PaymentCompletedEvent;
import com.inventoryservice.producer.InventoryEventProducer;
import com.inventoryservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Saga Step 2 Consumer — Inventory Service
 *
 * Listens to: payment-events (PaymentCompletedEvent)
 * Action: Reserve stock for the order
 * On success: publish InventoryReservedEvent → inventory-events
 * On failure: publish InventoryFailedEvent → inventory-events
 *
 * OPTIMISTIC LOCKING (how concurrent orders are handled safely):
 * The Inventory entity has a @Version field (set in Phase 7).
 * If two orders for the same product arrive simultaneously:
 *   Thread 1 reads Inventory(version=1, available=10)
 *   Thread 2 reads Inventory(version=1, available=10)
 *   Thread 1 saves → version becomes 2, available=8 (reserved 2)
 *   Thread 2 tries to save → version mismatch → OptimisticLockingFailureException
 *   Thread 2's listener catches it → publishes InventoryFailedEvent → payment refunded
 * This prevents overselling without pessimistic locks (which would kill throughput).
 *
 * WHY groupId="inventory-group"?
 * Completely separate from payment-group. Both consume from payment-events
 * but Kafka delivers each message to BOTH groups independently.
 * They never interfere with each other's offset tracking.
 *
 * IDEMPOTENCY: If this message is redelivered (consumer restart), reserveStock()
 * will try to reserve again. To make this truly idempotent, in production you'd
 * store a processed_events table with orderId and check before reserving.
 * For this project, the @Version optimistic locking provides natural protection.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final InventoryService inventoryService;
    private final InventoryEventProducer inventoryEventProducer;

    @KafkaListener(
            topics = "payment-events",
            groupId = "inventory-group",
            containerFactory = "inventoryKafkaListenerContainerFactory"
    )
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("InventoryService received PaymentCompletedEvent: orderId={}, productId={}, qty={}",
                event.getOrderId(), event.getProductId(), event.getQuantity());

        StockReservationRequest reservationRequest = StockReservationRequest.builder()
                .productId(event.getProductId())
                .quantity(event.getQuantity())
                .build();

        try {
            var inventoryResponse = inventoryService.reserveStock(reservationRequest);
            log.info("Stock reserved for orderId={}: productId={}, available={}, reserved={}",
                    event.getOrderId(), event.getProductId(),
                    inventoryResponse.getAvailableStock(), inventoryResponse.getReservedStock());

            // Publish SUCCESS event — triggers order CONFIRMED + notification
            InventoryReservedEvent reservedEvent = new InventoryReservedEvent(
                    event.getOrderId(),
                    event.getProductId(),
                    event.getQuantity()
            );
            inventoryEventProducer.publishInventoryReserved(reservedEvent);

        } catch (Exception e) {
            // Insufficient stock OR optimistic locking conflict → publish failure
            log.error("Stock reservation failed for orderId={}, productId={}: {}",
                    event.getOrderId(), event.getProductId(), e.getMessage());

            InventoryFailedEvent failedEvent = new InventoryFailedEvent(
                    event.getOrderId(),
                    event.getProductId(),
                    e.getMessage()
            );
            inventoryEventProducer.publishInventoryFailed(failedEvent);
        }
    }
}
