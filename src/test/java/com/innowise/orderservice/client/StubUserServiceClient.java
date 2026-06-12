package com.innowise.orderservice.client;

import com.innowise.orderservice.dto.UserDto;

public class StubUserServiceClient implements UserServiceClient {

    @Override
    public UserDto getUserById(Long userId) {
        return new UserDto(userId, "user@example.com", "John", "Doe");
    }
}
