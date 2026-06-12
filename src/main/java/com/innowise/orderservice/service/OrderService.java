package com.innowise.orderservice.service;

import com.innowise.orderservice.dto.CreateOrderRequest;
import com.innowise.orderservice.dto.OrderDto;
import com.innowise.orderservice.dto.OrderWithUserDto;
import com.innowise.orderservice.dto.UpdateOrderRequest;
import com.innowise.orderservice.repository.specification.OrderFilterRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface OrderService {

    OrderDto create(CreateOrderRequest request);

    OrderDto update(Long id, UpdateOrderRequest request);

    void delete(Long id);

    OrderWithUserDto getById(Long id);

    Page<OrderWithUserDto> getAll(OrderFilterRequest filter, Pageable pageable);

    List<OrderWithUserDto> getByUserId(Long userId);
}
