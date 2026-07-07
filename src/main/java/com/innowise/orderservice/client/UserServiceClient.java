package com.innowise.orderservice.client;

import com.innowise.orderservice.exception.UserServiceUnavailableException;
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

    /**
     * Looks up a user by id via User Service. Used to resolve the caller's own identity (email)
     * from the JWT's {@code sub} claim, so a real upstream outage must be distinguishable from
     * "no such user" — unlike {@link #getUserByEmail}, this method does not silently degrade.
     *
     * @param id the user's id
     * @return the user data, or {@code null} if the id genuinely does not exist (404)
     * @throws UserServiceUnavailableException if the circuit breaker is open or the service is unreachable/erroring
     */
    UserDto getUserById(Long id);
}
