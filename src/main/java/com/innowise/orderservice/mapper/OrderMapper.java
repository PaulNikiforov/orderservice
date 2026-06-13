package com.innowise.orderservice.mapper;

import com.innowise.orderservice.model.dto.OrderDto;
import com.innowise.orderservice.model.dto.OrderItemDto;
import com.innowise.orderservice.model.dto.OrderWithUserDto;
import com.innowise.orderservice.model.dto.UserDto;
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

    @Mapping(source = "order.id", target = "id")
    @Mapping(source = "order.status", target = "status")
    @Mapping(source = "order.totalPrice", target = "totalPrice")
    @Mapping(source = "order.items", target = "items")
    @Mapping(source = "order.createdAt", target = "createdAt")
    @Mapping(source = "order.updatedAt", target = "updatedAt")
    @Mapping(source = "user", target = "user")
    OrderWithUserDto toWithUserDto(Order order, UserDto user);
}
