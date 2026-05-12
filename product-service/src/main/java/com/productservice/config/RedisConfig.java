package com.productservice.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis Configuration — Cache-Aside Pattern for product-service.
 *
 * @EnableCaching: activates Spring's caching proxy infrastructure.
 * Without this, @Cacheable and @CacheEvict annotations are SILENTLY IGNORED.
 * This is the #1 mistake junior devs make — they add @Cacheable but forget @EnableCaching,
 * then wonder why there's no caching. The annotations do nothing without this.
 *
 * WHY LETTUCE over JEDIS?
 * - Lettuce is built on Netty (async, non-blocking I/O)
 * - Single connection shared across all threads via multiplexing
 * - Jedis is synchronous — needs a connection pool; each thread holds its own connection
 * - Under high concurrency (1000 req/s), Jedis needs 1000 connections; Lettuce needs ~1
 * - Spring Boot auto-configures Lettuce — just add the dependency
 *
 * SERIALIZATION CHOICE — GenericJackson2JsonRedisSerializer:
 * Redis stores bytes. We must serialize Java objects to bytes and back.
 *
 * Options:
 * 1. JdkSerializationRedisSerializer (default): Java binary serialization.
 *    Problem: opaque binary format, hard to inspect in Redis CLI,
 *    breaks if you rename/move classes, includes class metadata in every entry.
 *
 * 2. GenericJackson2JsonRedisSerializer (our choice): JSON format.
 *    Pros: human-readable in Redis CLI (redis-cli get "products::1" → valid JSON),
 *    easy to debug, survives minor class refactoring.
 *    Includes @class field for type info — needed to deserialize back to the right type.
 *
 * 3. Jackson2JsonRedisSerializer: JSON but without type info.
 *    Requires you to specify the type at config time — less flexible.
 *
 * TTL (Time To Live) = 10 minutes:
 * Product data doesn't change every second, but it does change (price updates, stock).
 * 10 minutes is a balance: stale cache for max 10 min, but 99%+ cache hit rate for reads.
 * In production, set TTL based on your business SLA:
 * - Flash sale prices: TTL = 30 seconds
 * - Product descriptions: TTL = 1 hour
 * - Shipping info: TTL = 24 hours
 *
 * CACHE KEY STRUCTURE:
 * Spring generates keys like "products::1", "products::2".
 * "products" = cache name (from @Cacheable(value = "products"))
 * "1"        = the method argument (product ID)
 * You can customize this with a KeyGenerator bean or SpEL expressions.
 */
@Configuration
@EnableCaching
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    /**
     * Lettuce connection factory — connects to Redis.
     * In production with Redis Sentinel (HA): use RedisSentinelConfiguration.
     * In production with Redis Cluster: use RedisClusterConfiguration.
     * For local dev: simple host:port is sufficient.
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(redisHost, redisPort);
    }

    /**
     * RedisTemplate: low-level Redis operations (get, set, delete, expire).
     * Used when you need manual cache control beyond @Cacheable/@CacheEvict.
     * Example: redis.opsForHash(), redis.opsForList() for complex data structures.
     *
     * Key serializer = StringRedisSerializer: keys stored as human-readable strings.
     * Value serializer = GenericJackson2JsonRedisSerializer: values stored as JSON.
     *
     * WHY separate key and value serializers?
     * Keys should be readable strings for easy debugging in Redis CLI.
     * Values need JSON (with type info) for proper Java object deserialization.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper()));
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper()));
        template.afterPropertiesSet();
        return template;
    }

    /**
     * CacheManager: the bridge between Spring's @Cacheable abstraction and Redis.
     * When @Cacheable sees a cache miss, it calls CacheManager.getCache("products")
     * which returns a RedisCacheManager-backed cache.
     *
     * RedisCacheConfiguration:
     * - defaultTtl(10 min): every cache entry expires after 10 minutes automatically
     * - serializeValuesWith: JSON serialization for values
     * - disableCachingNullValues(): if a product isn't found, don't cache null
     *   (otherwise "product not found" gets cached and masks real data added later)
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer(objectMapper())
                        )
                )
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }

    /**
     * Custom ObjectMapper for Redis serialization.
     * Must handle:
     * 1. Java 8+ date/time types (LocalDateTime) — requires JavaTimeModule
     * 2. Type information — requires activateDefaultTyping for GenericJackson2JsonRedisSerializer
     *    Without type info, Redis stores {"name":"Laptop"} and can't deserialize it back
     *    to ProductResponse (just gets a LinkedHashMap instead).
     */
    private ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.activateDefaultTyping(
                mapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return mapper;
    }
}
