package com.innowise.orderservice.client;

import com.innowise.orderservice.dto.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserServiceClientImpl implements UserServiceClient {

    private final RestClient restClient;

    @Override
    public UserDto getUserByEmail(String email) {
        return fetch(() -> restClient.get()
                .uri("/api/users/by-email?email={email}", email)
                .retrieve()
                .body(UserDto.class));
    }

    @Override
    public UserDto getUserById(Long userId) {
        return fetch(() -> restClient.get()
                .uri("/api/users/{id}", userId)
                .retrieve()
                .body(UserDto.class));
    }

    private UserDto fetch(Supplier<UserDto> call) {
        try {
            return call.get();
        } catch (RestClientException e) {
            log.warn("UserService call failed: {}", e.getMessage());
            return null;
        }
    }
}
