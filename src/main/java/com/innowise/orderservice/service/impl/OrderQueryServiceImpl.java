package com.innowise.orderservice.service.impl;

import com.innowise.orderservice.client.UserServiceClient;
import com.innowise.orderservice.exception.OrderNotFoundException;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.dto.OrderWithUserDto;
import com.innowise.orderservice.model.dto.UserDto;
import com.innowise.orderservice.repository.OrderRepository;
import com.innowise.orderservice.repository.specification.OrderFilterRequest;
import com.innowise.orderservice.repository.specification.OrderSpecification;
import com.innowise.orderservice.service.OrderQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderQueryServiceImpl implements OrderQueryService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final UserServiceClient userServiceClient;

    @Override
    @Transactional(readOnly = true)
    public OrderWithUserDto getById(Long id) {
        return toOrderWithUserDto(findOrderOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderWithUserDto> getAll(OrderFilterRequest filter, Pageable pageable) {
        Page<Order> page = orderRepository.findAll(OrderSpecification.fromFilter(filter), pageable);
        Map<Long, UserDto> userCache = new HashMap<>();
        for (Long uid : page.getContent().stream().map(Order::getUserId).distinct().toList()) {
            userCache.put(uid, userServiceClient.getUserById(uid));
        }
        return page.map(order -> orderMapper.toWithUserDto(order, userCache.get(order.getUserId())));
    }

    private OrderWithUserDto toOrderWithUserDto(Order order) {
        UserDto user = userServiceClient.getUserById(order.getUserId());
        return orderMapper.toWithUserDto(order, user);
    }

    private Order findOrderOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + id));
    }
}
