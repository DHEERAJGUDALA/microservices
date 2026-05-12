package com.productservice.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

/**
 * Elasticsearch Configuration for product-service.
 *
 * Spring Boot 3.x + Spring Data ES 5.x use the NEW Elasticsearch Java client (co.elastic.clients).
 * The OLD high-level REST client (org.elasticsearch.client) is DEPRECATED since ES 8.x.
 *
 * Client Architecture:
 * RestClient (HTTP layer — manages connection pool, retries, timeouts)
 *   └── RestClientTransport (adds JSON serialization via Jackson)
 *       └── ElasticsearchClient (typed API — document operations, search queries)
 *           └── ElasticsearchTemplate (Spring Data abstraction over ElasticsearchClient)
 *               └── ElasticsearchRepository (your repository interfaces)
 *
 * WHY NOT just use spring.elasticsearch.uris in properties?
 * For local dev, you can. But this explicit config is needed for:
 * 1. ES 8.x with SSL: you need to configure a custom SSLContext with the CA cert
 * 2. API key auth: RestClient.builder().setDefaultHeaders(...)
 * 3. Custom socket/connect timeouts for production tuning
 * 4. Multiple ES clusters (primary + replica cluster failover)
 *
 * For this project, we keep it simple (no auth, HTTP, local).
 * The structure is identical to production — just without SSL/certs.
 *
 * IMPORTANT: ES 8.x has security enabled by default (HTTPS + password).
 * Our Docker setup uses security disabled for local dev:
 * docker run -e "xpack.security.enabled=false" elasticsearch:8.11.0
 */
@Configuration
public class ElasticsearchConfig {

    @Value("${elasticsearch.host:localhost}")
    private String host;

    @Value("${elasticsearch.port:9200}")
    private int port;

    /**
     * Low-level REST client — manages HTTP connections to ES cluster.
     * In production: pass multiple HttpHost instances for a 3-node cluster.
     * RestClient handles failover automatically — if node 1 is down, it routes to node 2.
     */
    @Bean
    public RestClient restClient() {
        return RestClient.builder(
                new HttpHost(host, port, "http")
        ).build();
    }

    /**
     * Transport layer: wraps RestClient with JSON serialization.
     * JacksonJsonpMapper uses your existing Jackson ObjectMapper (already configured
     * by Spring Boot) to serialize/deserialize ES request and response JSON.
     */
    @Bean
    public ElasticsearchTransport elasticsearchTransport() {
        return new RestClientTransport(restClient(), new JacksonJsonpMapper());
    }

    /**
     * The typed ES client: the main entry point for all ES operations.
     * Used internally by ElasticsearchTemplate and ElasticsearchRepository.
     * You can also @Autowire this directly if you need low-level ES operations
     * not supported by Spring Data (like index management, reindexing, aliases).
     */
    @Bean
    public ElasticsearchClient elasticsearchClient() {
        return new ElasticsearchClient(elasticsearchTransport());
    }

    /**
     * Spring Data abstraction: provides save(), search(), delete() operations
     * that translate to ES API calls via ElasticsearchClient.
     * This is what ElasticsearchRepository implementations use internally.
     */
    @Bean
    public ElasticsearchOperations elasticsearchOperations() {
        return new ElasticsearchTemplate(elasticsearchClient());
    }
}
