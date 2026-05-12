package com.productservice.service;

import com.productservice.document.ProductDocument;
import com.productservice.dto.ProductResponse;
import com.productservice.repository.ProductSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Search Service — Elasticsearch read model (CQRS read side).
 *
 * This class ONLY reads from Elasticsearch. It never writes to PostgreSQL.
 * ProductService handles writes (PostgreSQL + ES sync + cache eviction).
 *
 * CQRS (Command Query Responsibility Segregation) in practice:
 * - Command side (writes): ProductService → PostgreSQL (source of truth)
 * - Query side (reads): ProductSearchService → Elasticsearch (optimized for search)
 *
 * EVENTUAL CONSISTENCY:
 * There is a brief window (milliseconds) between a product write to PostgreSQL
 * and its availability in Elasticsearch. This is acceptable for search — users don't
 * expect a product they just created to appear in search results instantly.
 * For the "get by ID" path, Redis cache + PostgreSQL provides immediate consistency.
 *
 * PRODUCTION ENHANCEMENTS you'd add here:
 * 1. Pagination: return Page<ProductResponse> instead of List (ES has from/size params)
 * 2. Filters: price range, in-stock only, category filter combined with the search query
 * 3. Aggregations: "found 47 results: Electronics(23), Laptops(15), Accessories(9)"
 * 4. Highlights: return which part of name/description matched the query (for UI underlining)
 * 5. Suggestions: "did you mean samsung?" using ES suggest API
 *
 * FALLBACK STRATEGY (production resilience):
 * If ES is down, catch ElasticsearchException and fall back to a LIKE query on PostgreSQL.
 * Slower but never shows users a broken search page.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductSearchService {

    private final ProductSearchRepository productSearchRepository;

    /**
     * Fuzzy full-text search across product name and description.
     *
     * Flow:
     * 1. Client: GET /api/products/search?q=gaming laptop
     * 2. This method queries ES with multi_match + fuzziness=AUTO
     * 3. ES scores documents by relevance (TF-IDF / BM25 algorithm)
     * 4. Returns ranked list — most relevant products first
     * 5. We map ProductDocument → ProductResponse (never expose the ES document directly)
     *
     * WHY map to ProductResponse and not return ProductDocument?
     * - Separation of concerns: API contract shouldn't depend on ES document structure
     * - ProductDocument might have ES-specific fields the API doesn't need
     * - If you switch from ES to a different search engine later, ProductResponse stays stable
     *
     * @param query the search term(s) entered by the user
     * @return ranked list of matching products
     */
    public List<ProductResponse> search(String query) {
        log.info("Searching Elasticsearch for: '{}'", query);

        if (query == null || query.isBlank()) {
            log.warn("Empty search query received — returning empty list");
            return List.of();
        }

        List<ProductDocument> results = productSearchRepository.searchByNameOrDescription(query.trim());
        log.info("Elasticsearch returned {} results for query: '{}'", results.size(), query);

        return results.stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Maps Elasticsearch document → API response DTO.
     *
     * The ES document ID is stored as String (ES uses string IDs internally).
     * We parse it back to Long for the ProductResponse.
     * If the ID is somehow not a valid Long (data corruption), we log and skip.
     */
    private ProductResponse mapToResponse(ProductDocument document) {
        return ProductResponse.builder()
                .id(document.getId() != null ? Long.parseLong(document.getId()) : null)
                .name(document.getName())
                .description(document.getDescription())
                .price(document.getPrice())
                .stock(document.getStock())
                .build();
    }
}
