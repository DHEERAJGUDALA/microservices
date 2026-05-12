package com.notificationservice.config;

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
 * Kafka Consumer Configuration for notification-service.
 *
 * notification-service listens to multiple topics:
 * - order-events: "order received" notification
 * - inventory-events: "order confirmed" or "order cancelled (out of stock)"
 * - payment-events: "order cancelled (payment failed)"
 *
 * It uses the default factory name "kafkaListenerContainerFactory" — that's fine
 * because notification-service only consumes, never produces.
 * The @KafkaListener annotations don't need to specify containerFactory
 * when using the default named factory.
 *
 * TYPE_MAPPINGS: All events from other services are mapped to notification-service's
 * local event class copies. This is necessary because the __TypeId__ header contains
 * the producer's package name (e.g., com.inventoryservice.event.InventoryReservedEvent)
 * which doesn't exist in notification-service's classpath.
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        // Map every event type → notification-service's local copy
        props.put(JsonDeserializer.TYPE_MAPPINGS,
                "com.orderservice.event.OrderCreatedEvent:com.notificationservice.event.OrderCreatedEvent," +
                "com.inventoryservice.event.InventoryReservedEvent:com.notificationservice.event.InventoryReservedEvent," +
                "com.inventoryservice.event.InventoryFailedEvent:com.notificationservice.event.InventoryFailedEvent," +
                "com.paymentservice.event.PaymentFailedEvent:com.notificationservice.event.PaymentFailedEvent");
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}
