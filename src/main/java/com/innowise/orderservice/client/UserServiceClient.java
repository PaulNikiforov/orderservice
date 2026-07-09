package com.innowise.orderservice.client;

import com.innowise.orderservice.exception.UserServiceUnavailableException;
import com.innowise.orderservice.model.dto.UserDto;

public interface UserServiceClient {

    UserDto getUserByEmail(String email);

    UserDto getUserById(Long id);
}
