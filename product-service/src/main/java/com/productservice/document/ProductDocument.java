package com.productservice.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;

/**
 * Elasticsearch document for product full-text search.
 *
 * @Document: marks this class as an ES index. Think of "index" like a DB table,
 * and each ProductDocument instance is one JSON document in that index.
 *
 * indexName = "products": the ES index name.
 * If this index doesn't exist, Spring Data ES creates it automatically on startup.
 *
 * WHY SEPARATE FROM Product.java (JPA entity)?
 * - Product.java owns the WRITE path: schema changes, DB migrations, ACID transactions
 * - ProductDocument.java owns the READ path: search ranking, fuzzy matching, relevance
 * - You can add ES-specific fields (like "searchableText") that don't exist in DB
 * - You can index different fields for search than what you store in DB
 * - They evolve independently — adding a new ES analyzer doesn't touch DB schema
 *
 * FIELD TYPES (critical for search behavior):
 *
 * FieldType.Text: The value is ANALYZED — split into tokens and indexed in inverted index.
 * Example: "Apple MacBook Pro" → tokens: ["apple", "macbook", "pro"]
 * Enables full-text search. Search "macbook" finds "Apple MacBook Pro".
 *
 * FieldType.Keyword: The value is NOT analyzed — stored and indexed as-is.
 * Used for exact matching, sorting, aggregations.
 * Example: category = "Laptop" must be searched as exactly "Laptop", not "laptop".
 *
 * FieldType.Double: Numeric field. Used for range queries (price > 500 AND price < 1000).
 *
 * FieldType.Integer: Integer numeric field for stock count.
 *
 * analyzer = "standard": The standard analyzer lowercases and tokenizes on whitespace/punctuation.
 * For production: use custom analyzers with stemming (english analyzer) or
 * edge_ngram for prefix search (autocomplete).
 *
 * searchAnalyzer = "standard": The analyzer used at QUERY time.
 * Should match the index-time analyzer so tokens are comparable.
 */
@Document(indexName = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDocument {

    /**
     * @Id here is Spring Data's generic ID (org.springframework.data.annotation.Id),
     * NOT JPA's @Id. ES uses string IDs — we store the PostgreSQL Long ID as a String.
     * This links the ES document back to the PostgreSQL record for updates/deletes.
     */
    @Id
    private String id;

    /**
     * FieldType.Text: analyzed for full-text search.
     * "Samsung Galaxy" → tokens: ["samsung", "galaxy"]
     * Searching "galaxy" returns this document.
     *
     * copyTo = "searchableText": copies this field's value into a combined search field.
     * Allows single-field multi-match queries: search across name + description simultaneously.
     */
    @Field(type = FieldType.Text, analyzer = "standard", searchAnalyzer = "standard")
    private String name;

    /**
     * FieldType.Text: analyzed — search within descriptions.
     * "High performance laptop for professionals" → tokens include "performance", "laptop", "professionals"
     */
    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    /**
     * FieldType.Double: stored as a numeric field.
     * Allows range queries: "find products priced between 500 and 1000"
     * Cannot use FieldType.Text for price — text fields can't do math comparisons.
     */
    @Field(type = FieldType.Double)
    private BigDecimal price;

    /**
     * FieldType.Integer: stock quantity as numeric.
     * Could filter: "only show in-stock products" (stock > 0)
     */
    @Field(type = FieldType.Integer)
    private Integer stock;
}
