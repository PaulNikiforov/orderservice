package com.innowise.orderservice.model.dto;

import java.math.BigDecimal;

public record OrderItemDto(
        Long id,
        Long itemId,
        String itemName,
        BigDecimal itemPrice,
        Integer quantity
) {}
