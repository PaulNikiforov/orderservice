package com.innowise.orderservice.service.impl;

import com.innowise.orderservice.client.UserServiceClient;
import com.innowise.orderservice.exception.OrderAccessDeniedException;
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
    public OrderWithUserDto getById(Long id, Long callerUserId, boolean admin) {
        Order order = findOrderOrThrow(id);
        if (!admin) {
            UserDto caller = userServiceClient.getUserById(callerUserId);
            String callerEmail = caller != null ? caller.email() : null;
            if (!order.getUserEmail().equals(callerEmail)) {
                throw new OrderAccessDeniedException("Access denied to order " + id);
            }
        }
        return toOrderWithUserDto(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderWithUserDto> getAll(OrderFilterRequest filter, Long callerUserId, boolean admin, Pageable pageable) {
        OrderFilterRequest effectiveFilter = filter;
        if (!admin) {
            UserDto caller = userServiceClient.getUserById(callerUserId);
            String callerEmail = caller != null ? caller.email() : null;
            effectiveFilter = new OrderFilterRequest(callerEmail, filter.statuses(), filter.createdFrom(), filter.createdTo());
        }
        Page<Order> page = orderRepository.findAll(OrderSpecification.fromFilter(effectiveFilter), pageable);
        Map<String, UserDto> userCache = new HashMap<>();
        for (String email : page.getContent().stream().map(Order::getUserEmail).distinct().toList()) {
            userCache.put(email, userServiceClient.getUserByEmail(email));
        }
        return page.map(order -> orderMapper.toWithUserDto(order, userCache.get(order.getUserEmail())));
    }

    private OrderWithUserDto toOrderWithUserDto(Order order) {
        UserDto user = userServiceClient.getUserByEmail(order.getUserEmail());
        return orderMapper.toWithUserDto(order, user);
    }

    private Order findOrderOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + id));
    }
}
