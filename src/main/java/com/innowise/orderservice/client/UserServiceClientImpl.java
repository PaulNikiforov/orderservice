package com.innowise.orderservice.client;

import com.innowise.orderservice.exception.UserServiceUnavailableException;
import com.innowise.orderservice.model.dto.UserDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserServiceClientImpl implements UserServiceClient {

    private final RestClient restClient;

    @CircuitBreaker(name = "userService", fallbackMethod = "fallback")
    @Override
    public UserDto getUserByEmail(String email) {
        try {
            return restClient.get()
                    .uri("/api/v1/users/by-email?email={email}", email)
                    .retrieve()
                    .body(UserDto.class);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    @CircuitBreaker(name = "userService", fallbackMethod = "fallbackById")
    @Override
    public UserDto getUserById(Long id) {
        try {
            return restClient.get()
                    .uri("/api/v1/users/{id}", id)
                    .retrieve()
                    .body(UserDto.class);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    UserDto fallback(String email, Throwable t) {
        log.warn("UserService unavailable for email={}: {}", email, t.getMessage());
        return null;
    }

    UserDto fallbackById(Long id, Throwable t) {
        log.warn("UserService unavailable for id={}: {}", id, t.getMessage());
        throw new UserServiceUnavailableException("User Service unavailable for id=" + id, t);
    }
}
