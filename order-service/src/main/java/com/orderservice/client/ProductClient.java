package com.orderservice.client;

import com.orderservice.dto.ProductDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client to communicate with product-service.
 * 
 * The name "product-service" must match the spring.application.name
 * of the product-service registered in Eureka.
 * 
 * fallback = ProductClientFallback.class:
 * - When product-service is down or circuit is open, fallback is used
 */
@FeignClient(name = "product-service", fallback = ProductClientFallback.class)
public interface ProductClient {

    @GetMapping("/api/products/{id}")
    ProductDto getProductById(@PathVariable("id") Long id);
}
