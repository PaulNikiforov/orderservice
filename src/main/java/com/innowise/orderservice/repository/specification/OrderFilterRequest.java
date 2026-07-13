package com.innowise.orderservice.repository.specification;

import com.innowise.orderservice.model.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public record OrderFilterRequest(String userEmail, List<OrderStatus> statuses,
                                 LocalDateTime createdFrom, LocalDateTime createdTo) {
}
