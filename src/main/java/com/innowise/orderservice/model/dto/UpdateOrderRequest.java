package com.innowise.orderservice.model.dto;

import com.innowise.orderservice.model.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderRequest(
        @NotNull OrderStatus status
) {}
