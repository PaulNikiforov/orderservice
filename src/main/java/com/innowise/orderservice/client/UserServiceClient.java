package com.innowise.orderservice.client;

import com.innowise.orderservice.dto.UserDto;

/**
 * Client for the external User Service. Implementations are expected to degrade
 * gracefully: when the User Service is unreachable or returns an error the call
 * resolves to {@code null} rather than propagating the failure.
 */
public interface UserServiceClient {

    /**
     * Fetches user data by id.
     *
     * @param userId the user id
     * @return the user, or {@code null} if the user is not found or the User Service is unavailable
     */
    UserDto getUserById(Long userId);
}
