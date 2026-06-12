package com.innowise.orderservice.mapper;

import com.innowise.orderservice.dto.OrderDto;
import com.innowise.orderservice.dto.OrderItemDto;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    OrderDto toDto(Order order);

    @Mapping(source = "item.id", target = "itemId")
    @Mapping(source = "item.name", target = "itemName")
    @Mapping(source = "item.price", target = "itemPrice")
    OrderItemDto toDto(OrderItem orderItem);
}
