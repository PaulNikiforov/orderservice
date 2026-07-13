package com.innowise.orderservice.model.dto;

import com.innowise.orderservice.model.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderWithUserDto(
        Long id,
        OrderStatus status,
        BigDecimal totalPrice,
        List<OrderItemDto> items,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        UserDto user
) {}
