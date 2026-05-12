package com.productservice.service;

import com.productservice.document.ProductDocument;
import com.productservice.dto.ProductRequest;
import com.productservice.dto.ProductResponse;
import com.productservice.entity.Product;
import com.productservice.repository.ProductRepository;
import com.productservice.repository.ProductSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ProductService — CQRS write side + Cache-Aside pattern.
 *
 * RESPONSIBILITIES:
 * 1. PostgreSQL CRUD (source of truth — always consistent)
 * 2. Elasticsearch sync (eventual consistency — search index stays up to date)
 * 3. Redis cache management (performance — sub-millisecond reads by ID)
 *
 * CACHE-ASIDE PATTERN (how it works step by step):
 *
 * READ (getProductById):
 *   @Cacheable checks: does "products::5" exist in Redis?
 *   YES → return it directly (0ms, no DB call) ← CACHE HIT
 *   NO  → call the method body → fetch from PostgreSQL → store in Redis → return ← CACHE MISS
 *
 * CREATE (createProduct):
 *   Method body runs (always — no cache to check on create)
 *   @CachePut stores result in Redis as "products::{newId}" immediately
 *   Next GET for this ID → cache HIT (no cold-start miss after create)
 *   ES index gets the document too → searchable immediately
 *
 * UPDATE (updateProduct):
 *   @CacheEvict removes "products::{id}" from Redis BEFORE method runs
 *   Method body updates PostgreSQL
 *   ES document gets updated too
 *   Next GET → cache MISS → fresh fetch from DB → re-cached ← correct fresh data
 *
 * DELETE (deleteProduct):
 *   @CacheEvict removes "products::{id}" from Redis
 *   Method body deletes from PostgreSQL
 *   ES document deleted too → not searchable anymore
 *
 * WHY NOT cache the list result of getAllProducts()?
 * Lists are dangerous to cache because:
 * 1. Cache invalidation is hard: which list entries changed when one product updates?
 * 2. Memory: caching 10,000 products as a list wastes Redis memory
 * 3. Staleness: list cache shows deleted products until TTL expires
 * Cache individual entities by ID — never entire collections in production.
 *
 * ES SYNC STRATEGY — "sync on write":
 * Every write (create/update/delete) immediately syncs to ES.
 * This gives near-real-time search (< 1 second latency for ES to index new docs).
 * Alternative: batch sync via a scheduled job or Debezium CDC — higher throughput
 * but more complex. For this scale, sync-on-write is correct.
 *
 * WHAT HAPPENS IF ES IS DOWN during a write?
 * The DB transaction commits successfully (PostgreSQL is independent of ES).
 * The ES sync call throws an exception. We catch it and LOG — we don't fail the request.
 * Why? The product IS created in the DB (source of truth). ES is an eventually-consistent
 * index. Failing the entire request because ES is down is wrong — customer loses their product.
 * In production: push to a retry queue (Kafka) so ES sync is retried when ES recovers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductSearchRepository productSearchRepository;

    /**
     * Create a new product.
     *
     * @CachePut: runs the method AND stores the result in cache.
     * key = "#result.id": uses the returned ProductResponse's id as cache key.
     * SpEL (Spring Expression Language): #result refers to the method's return value.
     *
     * Order of operations:
     * 1. Save to PostgreSQL
     * 2. Sync to Elasticsearch (search index)
     * 3. @CachePut stores ProductResponse in Redis
     */
    @Transactional
    @CachePut(value = "products", key = "#result.id")
    public ProductResponse createProduct(ProductRequest request) {
        log.info("Creating product: {}", request.getName());

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .stock(request.getStock())
                .build();

        Product saved = productRepository.save(product);
        log.info("Product saved to PostgreSQL with id={}", saved.getId());

        // Sync to Elasticsearch immediately — search picks up new product within ~1 second
        syncToElasticsearch(saved);

        return mapToResponse(saved);
    }

    /**
     * Get product by ID.
     *
     * @Cacheable: check Redis first, return if present (cache hit).
     * If not in Redis (cache miss), execute method body, then store result in Redis.
     * key = "#id": Redis key will be "products::1", "products::2", etc.
     *
     * unless = "#result == null": don't cache if product not found.
     * Without this: a 404 response gets cached and masks future valid products with that ID.
     *
     * WHAT YOU'D SEE in Redis CLI:
     * > GET products::1
     * → {"@class":"com.productservice.dto.ProductResponse","id":1,"name":"Laptop",...}
     */
    @Cacheable(value = "products", key = "#id", unless = "#result == null")
    public ProductResponse getProductById(Long id) {
        log.info("Cache MISS for product id={}. Fetching from PostgreSQL.", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));

        return mapToResponse(product);
    }

    /**
     * Get all products — NOT cached (see class javadoc for why).
     * Returns fresh data from PostgreSQL every time.
     */
    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Update a product.
     *
     * @CacheEvict: removes the stale cache entry BEFORE the method body runs.
     * Why before and not after? If the method throws an exception halfway through,
     * you don't want the old stale data staying in cache.
     * afterInvocation = false (default): evict before method body runs.
     *
     * After eviction + update: next GET for this ID will be a cache miss
     * → fresh data fetched from DB → re-cached with updated values.
     */
    @Transactional
    @CacheEvict(value = "products", key = "#id")
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        log.info("Updating product id={}. Cache entry evicted.", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());

        Product updated = productRepository.save(product);
        log.info("Product id={} updated in PostgreSQL", id);

        // Update Elasticsearch document (same ID — ES upserts on save)
        syncToElasticsearch(updated);

        return mapToResponse(updated);
    }

    /**
     * Delete a product.
     *
     * @CacheEvict: removes the cache entry.
     * Also deletes from Elasticsearch so it no longer appears in search results.
     */
    @Transactional
    @CacheEvict(value = "products", key = "#id")
    public void deleteProduct(Long id) {
        log.info("Deleting product id={}. Cache entry evicted.", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));

        productRepository.delete(product);
        log.info("Product id={} deleted from PostgreSQL", id);

        // Remove from Elasticsearch — won't appear in search results anymore
        try {
            productSearchRepository.deleteById(id.toString());
            log.info("Product id={} removed from Elasticsearch", id);
        } catch (Exception e) {
            log.error("Failed to delete product id={} from Elasticsearch: {}. DB delete succeeded.", id, e.getMessage());
        }
    }

    /**
     * Sync product to Elasticsearch.
     * Called on every create and update.
     *
     * ProductDocument.id = product.id.toString():
     * ES uses String IDs. We use the same ID as PostgreSQL so we can do exact lookups.
     * If PostgreSQL product id=42, ES document id="42".
     * On update, productSearchRepository.save() with id="42" UPSERTS (create if new, update if exists).
     *
     * TRY-CATCH: if ES is down, we log the error but DON'T fail the method.
     * The DB write already succeeded — the product exists. ES sync will drift temporarily.
     * In production: publish a "sync-failed" event to a retry topic (Kafka Dead Letter Queue).
     */
    private void syncToElasticsearch(Product product) {
        try {
            ProductDocument document = ProductDocument.builder()
                    .id(product.getId().toString())
                    .name(product.getName())
                    .description(product.getDescription())
                    .price(product.getPrice())
                    .stock(product.getStock())
                    .build();

            productSearchRepository.save(document);
            log.info("Product id={} synced to Elasticsearch", product.getId());
        } catch (Exception e) {
            log.error("Failed to sync product id={} to Elasticsearch: {}. PostgreSQL write succeeded.",
                    product.getId(), e.getMessage());
            // DO NOT re-throw — ES sync failure should not fail the product creation/update
        }
    }

    private ProductResponse mapToResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stock(product.getStock())
                .build();
    }
}