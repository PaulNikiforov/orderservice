package com.innowise.orderservice.service;

import com.innowise.orderservice.dto.CreateOrderRequest;
import com.innowise.orderservice.dto.OrderDto;
import com.innowise.orderservice.dto.OrderWithUserDto;
import com.innowise.orderservice.dto.UpdateOrderRequest;

public interface OrderCommandService {

    OrderDto create(CreateOrderRequest request);

    OrderWithUserDto update(Long id, UpdateOrderRequest request);

    void delete(Long id);
}
