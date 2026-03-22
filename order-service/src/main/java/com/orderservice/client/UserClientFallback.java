package com.orderservice.client;

import com.orderservice.dto.UserDto;
import com.orderservice.exception.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback implementation for UserClient.
 * 
 * When user-service is down or circuit is open, this class handles the request
 * instead of failing immediately.
 * 
 * Two fallback strategies:
 * 1. Return default/cached data (graceful degradation)
 * 2. Throw a clear exception (fail fast with good message)
 * 
 * We're using strategy 2 here - orders NEED valid user data,
 * so we can't fake it. But we give a clear error message.
 */
@Component
@Slf4j
public class UserClientFallback implements UserClient {

    @Override
    public UserDto getUserById(Long id) {
        log.error("FALLBACK: user-service is unavailable. Cannot fetch user with id={}", id);
        throw new ServiceUnavailableException("user-service");
    }
}
