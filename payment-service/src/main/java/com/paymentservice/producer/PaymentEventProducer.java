package com.paymentservice.producer;

import com.paymentservice.event.PaymentCompletedEvent;
import com.paymentservice.event.PaymentFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Centralizes all Kafka publishing for payment-service.
 *
 * Key design decisions:
 *
 * 1. MESSAGE KEY = orderId.toString()
 *    Kafka guarantees ordering within a partition.
 *    By using orderId as the key, ALL events for a given order
 *    (PaymentCompleted, PaymentFailed) go to the SAME partition.
 *    This means consumers see events in the exact order they were produced.
 *    Without this, event A could land on partition 2 and event B on partition 0,
 *    and a consumer might process B before A — race condition.
 *
 * 2. WHY KafkaTemplate<String, Object>?
 *    The generic Object type lets us publish any event type without creating
 *    separate templates. The JsonSerializer handles serialization of any POJO.
 *    The receiving service's JsonDeserializer deserializes back to the right class.
 *
 * 3. WHY @Value for topic names?
 *    If you hardcode "payment-events" everywhere and the topic is renamed,
 *    you hunt through every class. Injecting from config = one change propagates everywhere.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.kafka.topics.payment-events:payment-events}")
    private String paymentEventsTopic;

    /**
     * Publishes PaymentCompletedEvent after successful payment.
     * inventory-service consumes this to start stock reservation.
     * order-service consumes this to update order status to PAYMENT_COMPLETED.
     */
    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        log.info("Publishing PaymentCompletedEvent for orderId={}, transactionId={}",
                event.getOrderId(), event.getTransactionId());

        kafkaTemplate.send(paymentEventsTopic, event.getOrderId().toString(), event);

        log.info("PaymentCompletedEvent published to topic '{}' with key '{}'",
                paymentEventsTopic, event.getOrderId());
    }

    /**
     * Publishes PaymentFailedEvent after payment gateway rejection.
     * order-service consumes this to cancel the order immediately.
     * No inventory compensation needed — inventory step never started.
     */
    public void publishPaymentFailed(PaymentFailedEvent event) {
        log.error("Publishing PaymentFailedEvent for orderId={}, reason={}",
                event.getOrderId(), event.getReason());

        kafkaTemplate.send(paymentEventsTopic, event.getOrderId().toString(), event);

        log.info("PaymentFailedEvent published to topic '{}' with key '{}'",
                paymentEventsTopic, event.getOrderId());
    }
}
