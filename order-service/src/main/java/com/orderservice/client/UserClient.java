package com.orderservice.client;

import com.orderservice.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client to communicate with user-service.
 * 
 * The name "user-service" must match the spring.application.name
 * of the user-service registered in Eureka.
 * 
 * Feign + Eureka integration:
 * - Feign uses the service name to look up instances from Eureka
 * - Automatic load balancing if multiple instances exist
 * - No hardcoded URLs needed!
 * 
 * fallback = UserClientFallback.class:
 * - When user-service is down or circuit is open, fallback is used
 * - Provides graceful degradation instead of hard failure
 */
@FeignClient(name = "user-service", fallback = UserClientFallback.class)
public interface UserClient {

    @GetMapping("/api/users/{id}")
    UserDto getUserById(@PathVariable("id") Long id);
}
