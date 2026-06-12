package com.innowise.orderservice.repository.specification;

import com.innowise.orderservice.model.OrderStatus;

import java.time.LocalDateTime;

public record OrderFilterRequest(Long userId, OrderStatus status,
                                 LocalDateTime createdFrom, LocalDateTime createdTo) {
}
