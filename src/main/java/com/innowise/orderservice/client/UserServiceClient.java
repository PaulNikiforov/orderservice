package com.innowise.orderservice.client;

import com.innowise.orderservice.dto.UserDto;

public interface UserServiceClient {

    UserDto getUserByEmail(String email);

    UserDto getUserById(Long userId);
}
