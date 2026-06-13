package com.innowise.orderservice.model.dto;

import com.innowise.orderservice.model.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderDto(
        Long id,
        Long userId,
        OrderStatus status,
        BigDecimal totalPrice,
        List<OrderItemDto> items,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
