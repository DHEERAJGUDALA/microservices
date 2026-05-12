package com.productservice.controller;

import com.productservice.dto.ProductRequest;
import com.productservice.dto.ProductResponse;
import com.productservice.service.ProductSearchService;
import com.productservice.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ProductController — REST API for product management and search.
 *
 * ENDPOINT DESIGN:
 *
 * POST   /api/products              — create product
 * GET    /api/products/{id}         — get by ID (cache-aside via Redis)
 * GET    /api/products              — get all products (no cache — see ProductService)
 * PUT    /api/products/{id}         — update product (evicts Redis cache)
 * DELETE /api/products/{id}         — delete product (evicts Redis cache)
 * GET    /api/products/search?q=... — fuzzy search via Elasticsearch ← NEW
 *
 * WHY @RequestMapping("/api/products") and NOT "/products"?
 * The old controller used "/products". With an API gateway routing "/api/products/**",
 * the gateway strips the prefix and forwards to product-service.
 * Consistent "/api/" prefix = clear convention across all services.
 *
 * @Valid on @RequestBody: triggers Jakarta Bean Validation on ProductRequest.
 * If any constraint (@NotBlank, @DecimalMin) is violated, Spring automatically
 * returns HTTP 400 with a structured error response — no manual validation code.
 *
 * HTTP Status codes:
 * - 201 CREATED for POST (resource was created, not just "ok")
 * - 200 OK for GET, PUT
 * - 204 NO CONTENT for DELETE (success but nothing to return)
 * Using 200 for POST is an anti-pattern — REST convention says 201 for resource creation.
 *
 * @Slf4j + request logging: lets you trace exactly what endpoint was called and with what
 * parameters — critical for debugging production issues.
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Slf4j
public class ProductController {

    private final ProductService productService;
    private final ProductSearchService productSearchService;

    /**
     * Create a new product.
     * Returns 201 CREATED with the created product (including generated ID).
     */
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        log.info("POST /api/products — creating product: {}", request.getName());
        ProductResponse response = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get product by ID.
     * First call: PostgreSQL fetch (cache miss — Redis populated).
     * Subsequent calls within 10 min: Redis hit (0ms response).
     *
     * You can verify caching in Redis CLI:
     * redis-cli> KEYS products::*        → shows all cached product keys
     * redis-cli> GET "products::1"       → shows the JSON-serialized ProductResponse
     * redis-cli> TTL "products::1"       → shows seconds remaining before expiry
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id) {
        log.info("GET /api/products/{}", id);
        return ResponseEntity.ok(productService.getProductById(id));
    }

    /**
     * Get all products.
     * No caching — fresh from PostgreSQL every time (see ProductService for why).
     */
    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        log.info("GET /api/products — fetching all products");
        return ResponseEntity.ok(productService.getAllProducts());
    }

    /**
     * Update an existing product.
     * Cache entry for this ID is evicted before update runs.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {
        log.info("PUT /api/products/{}", id);
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    /**
     * Delete a product.
     * Removes from PostgreSQL, Elasticsearch, and Redis.
     * Returns 204 NO CONTENT — correct REST convention for successful delete.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        log.info("DELETE /api/products/{}", id);
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Fuzzy full-text search via Elasticsearch.
     *
     * Example: GET /api/products/search?q=gaming laptop
     * → Returns products where name or description fuzzy-matches "gaming" OR "laptop"
     * → Sorted by relevance (name matches rank higher than description matches)
     *
     * WHY @RequestParam with defaultValue=""?
     * If no query is provided (GET /api/products/search), return empty list — not an error.
     * defaultValue prevents a 400 Bad Request when q is missing.
     *
     * IMPORTANT — route ordering:
     * "/api/products/search" must be matched BEFORE "/api/products/{id}".
     * Spring MVC handles this correctly — literal path segments (search) take priority
     * over path variables ({id}). No ordering annotation needed.
     *
     * WHY NOT cache search results?
     * Search results are highly dynamic — they change with every product create/update/delete.
     * Caching search results would show stale product lists.
     * ES is already sub-10ms for most queries — caching search adds complexity with minimal gain.
     */
    @GetMapping("/search")
    public ResponseEntity<List<ProductResponse>> searchProducts(
            @RequestParam(defaultValue = "") String q) {
        log.info("GET /api/products/search?q={}", q);
        List<ProductResponse> results = productSearchService.search(q);
        return ResponseEntity.ok(results);
    }
}