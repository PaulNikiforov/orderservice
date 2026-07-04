package com.innowise.orderservice.kafka;

public record PaymentCompletedEvent(String orderId, PaymentStatus status) {
}
