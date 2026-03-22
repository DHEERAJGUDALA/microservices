package com.orderservice.client;

import com.orderservice.dto.ProductDto;
import com.orderservice.exception.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback implementation for ProductClient.
 * 
 * Called when product-service is down or circuit breaker is open.
 */
@Component
@Slf4j
public class ProductClientFallback implements ProductClient {

    @Override
    public ProductDto getProductById(Long id) {
        log.error("FALLBACK: product-service is unavailable. Cannot fetch product with id={}", id);
        throw new ServiceUnavailableException("product-service");
    }
}
