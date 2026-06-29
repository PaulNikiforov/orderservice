package com.innowise.orderservice.client;

import com.innowise.orderservice.model.dto.UserDto;

public interface UserServiceClient {

    /**
     * Looks up a user by email via User Service.
     * Returns {@code null} when the circuit breaker is open or the service returns a non-2xx response.
     *
     * @param email the user's email address
     * @return the user data, or {@code null} on degradation
     */
    UserDto getUserByEmail(String email);
}
