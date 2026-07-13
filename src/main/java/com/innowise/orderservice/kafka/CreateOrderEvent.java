package com.innowise.orderservice.kafka;

import java.math.BigDecimal;

public record CreateOrderEvent(String orderId, String userId, BigDecimal amount) {
}
