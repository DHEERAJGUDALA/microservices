package com.paymentservice.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Configuration for payment-service.
 *
 * PRODUCER: publishes PaymentCompletedEvent and PaymentFailedEvent to payment-events
 * CONSUMER: consumes from order-events (OrderCreatedEvent) and inventory-events (InventoryFailedEvent)
 *
 * KEY DESIGN — TYPE_MAPPINGS:
 * The producer embeds the full class name in the __TypeId__ header:
 * e.g., com.orderservice.event.OrderCreatedEvent
 *
 * The consumer can't find that class (different service, different package).
 * TYPE_MAPPINGS tell the deserializer: "when you see 'OrderCreatedEvent' in the header,
 * instantiate com.paymentservice.event.OrderCreatedEvent instead."
 *
 * Format: "logicalName:fully.qualified.ClassName,logicalName2:fully.qualified.ClassName2"
 * The logical name MUST match what the producer set as the type ID.
 *
 * PRODUCER SETTINGS (acks=all, idempotence=true, retries=3):
 * - acks=all: Kafka broker waits for ALL in-sync replicas to acknowledge before confirming.
 *   Without this, a broker crash after receiving but before replicating loses the message.
 * - enable.idempotence=true: The producer assigns a sequence number to each message.
 *   If the network fails and the broker already wrote the message, a retry is detected
 *   and the duplicate is discarded. Without this, retries could double-charge customers.
 * - retries=3: Combined with idempotence, safe to retry on transient failures.
 *
 * BEAN NAME = "paymentKafkaListenerContainerFactory":
 * Named explicitly so @KafkaListener(containerFactory="paymentKafkaListenerContainerFactory")
 * uses THIS config, not the auto-configured default one (which lacks our type mappings).
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // ==================== PRODUCER CONFIG ====================

    @Bean
    public ProducerFactory<String, Object> paymentProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // acks=all: strongest durability guarantee — all replicas must acknowledge
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        // retries=3: retry on transient network errors (safe because idempotence is on)
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        // idempotence=true: prevent duplicate messages on retry (critical for payments!)
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(paymentProducerFactory());
    }

    // ==================== CONSUMER CONFIG ====================

    @Bean
    public ConsumerFactory<String, Object> paymentConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "payment-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // ErrorHandlingDeserializer wraps JsonDeserializer so malformed JSON doesn't crash the consumer
        // Without it, a bad message kills the partition consumer and stops processing forever
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        // TYPE_MAPPINGS: map logical event type name → local class
        // Producer sends __TypeId__ = "com.orderservice.event.OrderCreatedEvent"
        // We map that to our local copy in com.paymentservice.event
        props.put(JsonDeserializer.TYPE_MAPPINGS,
                "com.orderservice.event.OrderCreatedEvent:com.paymentservice.event.OrderCreatedEvent," +
                "com.inventoryservice.event.InventoryFailedEvent:com.paymentservice.event.InventoryFailedEvent," +
                "com.paymentservice.event.PaymentCompletedEvent:com.paymentservice.event.PaymentCompletedEvent," +
                "com.paymentservice.event.PaymentFailedEvent:com.paymentservice.event.PaymentFailedEvent");
        // TRUSTED_PACKAGES: fallback for any unmapped types
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        // earliest: if this consumer group has no committed offset, start from the beginning of the topic
        // Useful on first startup — won't miss orders placed before payment-service was started
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> paymentKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(paymentConsumerFactory());
        return factory;
    }
}
