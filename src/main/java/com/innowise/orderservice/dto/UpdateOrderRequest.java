package com.innowise.orderservice.dto;

import com.innowise.orderservice.model.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderRequest(
        @NotNull OrderStatus status
) {}
