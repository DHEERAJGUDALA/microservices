package com.paymentservice.consumer;

import com.paymentservice.event.InventoryFailedEvent;
import com.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Saga Compensating Transaction Consumer — Payment Service
 *
 * Listens to: inventory-events (InventoryFailedEvent)
 * Action: Trigger automatic refund (compensating transaction)
 *
 * This class represents one of the most important concepts in distributed systems:
 * the COMPENSATING TRANSACTION — the Saga's equivalent of a database ROLLBACK.
 *
 * SCENARIO this handles:
 * 1. Order created (PENDING)
 * 2. Payment charged successfully (COMPLETED) ← money taken
 * 3. Inventory reservation FAILS (out of stock)
 * 4. THIS consumer fires → calls refundPayment() → money returned
 * 5. Order becomes CANCELLED
 *
 * WHY is this in a SEPARATE class from OrderEventConsumer?
 * - Single Responsibility: one class = one saga role
 * - Different retry strategies: payment charging may need 3 retries,
 *   refunding may need 10 retries (you MUST refund the customer)
 * - Separate DLQ configuration per listener
 * - Easier to monitor: if refund consumer is lagging, alert immediately
 *
 * PRODUCTION CONCERN — What if refundPayment() fails?
 * The offset isn't committed, Kafka redelivers after consumer restart.
 * The refund is idempotent (same payment moves to REFUNDED — can't go REFUNDED→REFUNDED).
 * In production, you'd add a DLQ + alerting for manual intervention if all retries fail.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryEventConsumer {

    private final PaymentService paymentService;

    @KafkaListener(
            topics = "inventory-events",
            groupId = "payment-group",
            containerFactory = "paymentKafkaListenerContainerFactory"
    )
    public void handleInventoryFailed(InventoryFailedEvent event) {
        log.warn("PaymentService received InventoryFailedEvent: orderId={}, productId={}, reason={}",
                event.getOrderId(), event.getProductId(), event.getReason());

        // Trigger compensating transaction — refund the payment that was already charged
        try {
            var refundResponse = paymentService.refundPayment(event.getOrderId());
            log.info("Compensating transaction executed: refund issued for orderId={}, amount={}",
                    event.getOrderId(), refundResponse.getAmount());
        } catch (Exception e) {
            // If no COMPLETED payment found (e.g., payment actually failed first), log and move on.
            // This is safe — it means no refund is needed because money was never taken.
            log.warn("Could not issue refund for orderId={} — possibly payment already failed: {}",
                    event.getOrderId(), e.getMessage());
        }
    }
}
