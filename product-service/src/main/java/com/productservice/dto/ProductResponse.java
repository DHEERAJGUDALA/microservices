package com.productservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * API response DTO for product data.
 *
 * WHY a separate DTO instead of returning the Product entity directly?
 *
 * 1. Decoupling: Renaming a DB column (e.g., "stock" → "quantity_available") would
 *    break the API if we returned the entity. The DTO is the API contract — stable.
 *
 * 2. Redis serialization: JPA entities have Hibernate proxies and lazy-loaded
 *    collections that can't be cleanly serialized. A plain POJO DTO serializes perfectly.
 *
 * 3. Security: Entities might have internal fields (version numbers, audit fields,
 *    internal IDs) you don't want to expose in the API response.
 *
 * 4. Flexibility: You can add computed fields to the DTO (e.g., "isInStock" = stock > 0)
 *    without changing the DB schema.
 *
 * implements Serializable:
 * Required for Spring Cache to serialize this object into Redis.
 * Without it, you get a NotSerializableException at runtime when caching.
 * GenericJackson2JsonRedisSerializer also needs the class to be deserializable via Jackson
 * (all fields must have accessible getters — @Data handles this via Lombok).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse implements Serializable {

    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stock;

    // Computed convenience field — derived from stock, not stored in DB
    // Client doesn't need to check "stock > 0" — the API tells them directly
    public boolean isInStock() {
        return stock != null && stock > 0;
    }
}
