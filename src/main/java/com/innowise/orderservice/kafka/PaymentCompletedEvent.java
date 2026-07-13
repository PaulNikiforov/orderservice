package com.innowise.orderservice.kafka;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentCompletedEvent(@NotBlank String orderId, @NotNull PaymentStatus status) {
}
