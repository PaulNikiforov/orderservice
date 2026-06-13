package com.innowise.orderservice.client;

import com.innowise.orderservice.model.dto.UserDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserServiceClientImpl implements UserServiceClient {

    private final RestClient restClient;

    @CircuitBreaker(name = "userService", fallbackMethod = "fallback")
    @Override
    public UserDto getUserById(Long userId) {
        return restClient.get()
                .uri("/api/users/{id}", userId)
                .retrieve()
                .body(UserDto.class);
    }

    /**
     * Resilience4j fallback for {@link #getUserById}: returns {@code null} so callers
     * can degrade gracefully instead of propagating the failure.
     */
    UserDto fallback(Long userId, Throwable t) {
        log.warn("UserService unavailable for userId={}: {}", userId, t.getMessage());
        return null;
    }
}
