package com.productservice.repository;

import com.productservice.document.ProductDocument;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

/**
 * Spring Data Elasticsearch repository for ProductDocument.
 *
 * ElasticsearchRepository<ProductDocument, String>:
 * - First generic = document type
 * - Second generic = ID type (String — ES uses string IDs)
 *
 * Spring Data generates implementations for:
 * - save(document)          → POST /products/_doc/{id}
 * - findById(id)            → GET /products/_doc/{id}
 * - delete(document)        → DELETE /products/_doc/{id}
 * - findAll()               → GET /products/_search (match_all)
 *
 * CUSTOM QUERY — @Query with ES Query DSL:
 *
 * multi_match: searches across MULTIPLE fields simultaneously.
 * Without this, you'd need a separate query per field.
 *
 * fuzziness: "AUTO" — ES automatically calculates edit distance based on term length:
 * - 1-2 chars: exact match only (too short to fuzzy)
 * - 3-5 chars: 1 edit distance allowed ("lapto" finds "laptop")
 * - 6+ chars:  2 edit distances allowed ("samsong" finds "samsung")
 *
 * Why "AUTO" over a fixed number?
 * "laptop" (6 chars) with fuzziness=1 finds "loptop" but not "loptop2".
 * AUTO adapts: short words need exact match (fuzzy on "TV" would match too many things).
 *
 * operator: "OR" — "apple laptop" returns products containing "apple" OR "laptop".
 * Use "AND" if you want all terms to be present (stricter, fewer results).
 *
 * ?0 is the positional parameter — replaced by the method argument at runtime.
 *
 * PRODUCTION NOTE: For autocomplete (as-you-type search),
 * add a separate field with edge_ngram analyzer and query it separately.
 * For synonym support (tv → television), configure a synonym token filter in ES mapping.
 */
public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, String> {

    /**
     * Fuzzy full-text search across name and description.
     * Weights name higher (^2 = boost factor): a match in name scores 2x vs a match in description.
     * This means "laptop" in the product NAME ranks higher than "laptop" in the DESCRIPTION.
     *
     * Example: search "gaming laptop"
     * → ES finds all documents where name OR description contains "gaming" OR "laptop"
     * → Products with both terms in name rank highest
     * → Products with "gaming" in name and "laptop" in description rank next
     * → Sorted by relevance score descending
     */
    @Query("""
            {
              "multi_match": {
                "query": "?0",
                "fields": ["name^2", "description"],
                "fuzziness": "AUTO",
                "operator": "OR"
              }
            }
            """)
    List<ProductDocument> searchByNameOrDescription(String query);
}
