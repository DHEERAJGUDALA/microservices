package com.orderservice.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();

        // Where to find the Kafka broker(s)
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Key serializer: we use orderId as the key (String)
        // Using the same key guarantees all events for one order go to the same partition → ordering
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // Value serializer: our event objects are serialized to JSON
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // acks=all: wait for ALL replicas to confirm. Strongest guarantee against data loss.
        // In our single-broker dev setup this means just the leader, but in production
        // with 3 brokers, all 3 must confirm before the producer considers the send successful.
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        // Retries: if a send fails (broker unreachable, leader election in progress),
        // retry up to 3 times before throwing an exception
        props.put(ProducerConfig.RETRIES_CONFIG, 3);

        // Idempotent producer: even if a retry succeeds but the ACK was lost (so the producer
        // retries again), Kafka deduplicates using a sequence number. No duplicate messages.
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
