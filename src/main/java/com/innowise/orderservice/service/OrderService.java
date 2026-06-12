package com.innowise.orderservice.service;

import com.innowise.orderservice.dto.CreateOrderRequest;
import com.innowise.orderservice.dto.OrderDto;
import com.innowise.orderservice.dto.UpdateOrderRequest;

public interface OrderService {

    OrderDto create(CreateOrderRequest request);

    OrderDto update(Long id, UpdateOrderRequest request);

    void delete(Long id);
}
