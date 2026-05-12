package com.paymentservice.consumer;

import com.paymentservice.dto.PaymentRequest;
import com.paymentservice.entity.Payment;
import com.paymentservice.event.OrderCreatedEvent;
import com.paymentservice.event.PaymentCompletedEvent;
import com.paymentservice.event.PaymentFailedEvent;
import com.paymentservice.producer.PaymentEventProducer;
import com.paymentservice.repository.PaymentRepository;
import com.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Saga Step 1 Consumer — Payment Service
 *
 * Listens to: order-events (OrderCreatedEvent)
 * Action: Process payment
 * On success: publish PaymentCompletedEvent → payment-events
 * On failure: publish PaymentFailedEvent → payment-events
 *
 * WHY groupId="payment-group"?
 * This ties all instances of payment-service to the SAME consumer group.
 * If you run 3 instances of payment-service for high availability,
 * Kafka will distribute partitions across them — only ONE instance processes
 * each message. No duplicate payments. This is load balancing built into Kafka.
 *
 * WHY containerFactory="paymentKafkaListenerContainerFactory"?
 * The KafkaConfig in this service defines a specific factory that knows how to
 * deserialize JSON into payment-service's event classes with ErrorHandlingDeserializer.
 * Without naming the factory, Spring would use the default one which may not
 * have trusted packages configured — causing ClassNotFound deserialization errors.
 *
 * IDEMPOTENCY via transactionId:
 * If Kafka redelivers this message (e.g., consumer restarted before committing offset),
 * processPayment() checks if transactionId already exists and returns the existing payment.
 * This prevents double-charging the customer on message redelivery.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final PaymentService paymentService;
    private final PaymentEventProducer paymentEventProducer;
    private final PaymentRepository paymentRepository;

    @KafkaListener(
            topics = "order-events",
            groupId = "payment-group",
            containerFactory = "paymentKafkaListenerContainerFactory"
    )
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("PaymentService received OrderCreatedEvent: orderId={}, userId={}, amount={}",
                event.getOrderId(), event.getUserId(), event.getTotalPrice());

        // Generate a deterministic transactionId from orderId.
        // If the message is redelivered, the same transactionId is generated,
        // and processPayment() idempotency check returns the existing result — no double charge.
        String transactionId = "TXN-ORDER-" + event.getOrderId();

        PaymentRequest request = PaymentRequest.builder()
                .orderId(event.getOrderId())
                .userId(event.getUserId())
                .amount(event.getTotalPrice())
                .transactionId(transactionId)
                .build();

        try {
            var paymentResponse = paymentService.processPayment(request);
            log.info("Payment processed: status={} for orderId={}", paymentResponse.getStatus(), event.getOrderId());

            if ("COMPLETED".equals(paymentResponse.getStatus())) {
                // Publish success event — inventory-service and order-service will pick this up
                PaymentCompletedEvent completedEvent = new PaymentCompletedEvent(
                        event.getOrderId(),
                        event.getUserId(),
                        event.getProductId(),
                        event.getQuantity(),
                        paymentResponse.getTransactionId(),
                        paymentResponse.getAmount()
                );
                paymentEventProducer.publishPaymentCompleted(completedEvent);

            } else {
                // FAILED status — publish failure event so order gets CANCELLED
                PaymentFailedEvent failedEvent = new PaymentFailedEvent(
                        event.getOrderId(),
                        event.getUserId(),
                        paymentResponse.getFailureReason() != null
                                ? paymentResponse.getFailureReason()
                                : "Payment failed"
                );
                paymentEventProducer.publishPaymentFailed(failedEvent);
            }

        } catch (Exception e) {
            // Catch unexpected errors — still publish failure so order doesn't hang in PENDING forever
            log.error("Unexpected error processing payment for orderId={}: {}", event.getOrderId(), e.getMessage(), e);
            PaymentFailedEvent failedEvent = new PaymentFailedEvent(
                    event.getOrderId(),
                    event.getUserId(),
                    "Internal payment processing error: " + e.getMessage()
            );
            paymentEventProducer.publishPaymentFailed(failedEvent);
        }
    }
}
