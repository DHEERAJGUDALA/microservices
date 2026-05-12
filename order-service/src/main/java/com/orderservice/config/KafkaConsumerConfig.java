package com.orderservice.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Consumer Configuration for order-service.
 *
 * Order-service was previously ONLY a producer (it published OrderCreatedEvent).
 * Now it's also a CONSUMER — it needs to listen to:
 * - payment-events: to update order status when payment completes/fails
 * - inventory-events: to set the final order state (CONFIRMED or CANCELLED)
 *
 * WHY a separate KafkaConsumerConfig instead of adding to KafkaProducerConfig?
 * Producer and consumer have completely different config keys.
 * Splitting keeps each config focused and easier to tune independently.
 * Production teams tune consumer fetch.min.bytes, max.poll.records, session.timeout
 * separately from producer batch.size, linger.ms, buffer.memory.
 *
 * TYPE_MAPPINGS for order-service:
 * - payment-service publishes PaymentCompletedEvent and PaymentFailedEvent
 *   with __TypeId__ = "com.paymentservice.event.*"
 *   We map to "com.orderservice.event.*" (our local copies)
 * - inventory-service publishes InventoryReservedEvent and InventoryFailedEvent
 *   with __TypeId__ = "com.inventoryservice.event.*"
 *   We map to "com.orderservice.event.*"
 *
 * groupId = "order-saga-group":
 * Completely isolated from payment-group and inventory-group.
 * All three can consume from the same topic without interfering.
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, Object> orderConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "order-saga-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        // Map all event types from other services → order-service's local event classes
        props.put(JsonDeserializer.TYPE_MAPPINGS,
                "com.paymentservice.event.PaymentCompletedEvent:com.orderservice.event.PaymentCompletedEvent," +
                "com.paymentservice.event.PaymentFailedEvent:com.orderservice.event.PaymentFailedEvent," +
                "com.inventoryservice.event.InventoryReservedEvent:com.orderservice.event.InventoryReservedEvent," +
                "com.inventoryservice.event.InventoryFailedEvent:com.orderservice.event.InventoryFailedEvent");
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> orderKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(orderConsumerFactory());
        return factory;
    }
}
