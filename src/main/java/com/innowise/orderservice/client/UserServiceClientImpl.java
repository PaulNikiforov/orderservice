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
    public UserDto getUserByEmail(String email) {
        return restClient.get()
                .uri("/api/v1/users/by-email?email={email}", email)
                .retrieve()
                .body(UserDto.class);
    }

    UserDto fallback(String email, Throwable t) {
        log.warn("UserService unavailable for email={}: {}", email, t.getMessage());
        return null;
    }
}
