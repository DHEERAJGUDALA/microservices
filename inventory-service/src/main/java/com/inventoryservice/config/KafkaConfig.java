package com.inventoryservice.config;

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
 * Kafka Configuration for inventory-service.
 *
 * PRODUCER: publishes InventoryReservedEvent and InventoryFailedEvent to inventory-events
 * CONSUMER: consumes from payment-events (PaymentCompletedEvent)
 *
 * TYPE_MAPPINGS explanation:
 * payment-service publishes PaymentCompletedEvent with __TypeId__ header =
 * "com.paymentservice.event.PaymentCompletedEvent"
 * inventory-service has its own copy in "com.inventoryservice.event.PaymentCompletedEvent"
 * Without mapping, deserialization throws ClassNotFoundException.
 * With mapping, JsonDeserializer instantiates our local class instead.
 *
 * CONCURRENCY NOTE:
 * ConcurrentKafkaListenerContainerFactory with concurrency=3 means 3 threads
 * each consuming from one Kafka partition (we have 3 partitions on payment-events).
 * This triples throughput — critical for high-order-volume scenarios.
 * Each thread processes messages from its assigned partition independently.
 * Ordering is preserved within a partition (all events for orderId=42 go to same partition).
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // ==================== PRODUCER CONFIG ====================

    @Bean
    public ProducerFactory<String, Object> inventoryProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(inventoryProducerFactory());
    }

    // ==================== CONSUMER CONFIG ====================

    @Bean
    public ConsumerFactory<String, Object> inventoryConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "inventory-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        // Map payment-service's class name → inventory-service's local class
        props.put(JsonDeserializer.TYPE_MAPPINGS,
                "com.paymentservice.event.PaymentCompletedEvent:com.inventoryservice.event.PaymentCompletedEvent," +
                "com.inventoryservice.event.InventoryReservedEvent:com.inventoryservice.event.InventoryReservedEvent," +
                "com.inventoryservice.event.InventoryFailedEvent:com.inventoryservice.event.InventoryFailedEvent");
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> inventoryKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(inventoryConsumerFactory());
        // 3 concurrent consumer threads — one per Kafka partition
        // Allows parallel processing of events for different orders simultaneously
        factory.setConcurrency(3);
        return factory;
    }
}
