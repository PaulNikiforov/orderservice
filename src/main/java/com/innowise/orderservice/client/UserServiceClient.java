package com.innowise.orderservice.client;

import com.innowise.orderservice.dto.UserDto;

public interface UserServiceClient {

    UserDto getUserById(Long userId);
}
