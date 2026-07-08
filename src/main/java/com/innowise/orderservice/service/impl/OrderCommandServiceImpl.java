package com.innowise.orderservice.service.impl;

import com.innowise.orderservice.client.UserServiceClient;
import com.innowise.orderservice.exception.ItemNotFoundException;
import com.innowise.orderservice.exception.OrderAccessDeniedException;
import com.innowise.orderservice.exception.OrderNotFoundException;
import com.innowise.orderservice.kafka.CreateOrderEvent;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.model.Item;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderItem;
import com.innowise.orderservice.model.dto.CreateOrderRequest;
import com.innowise.orderservice.model.dto.OrderItemRequest;
import com.innowise.orderservice.model.dto.OrderWithUserDto;
import com.innowise.orderservice.model.dto.UpdateOrderRequest;
import com.innowise.orderservice.model.dto.UserDto;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import com.innowise.orderservice.service.OrderCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderCommandServiceImpl implements OrderCommandService {

    private final OrderRepository orderRepository;
    private final ItemRepository itemRepository;
    private final OrderMapper orderMapper;
    private final UserServiceClient userServiceClient;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public OrderWithUserDto create(CreateOrderRequest request, Long callerUserId) {
        Order order = new Order();
        UserDto caller = userServiceClient.getUserById(callerUserId);
        order.setUserEmail(caller != null ? caller.email() : null);

        List<Long> itemIds = request.items().stream().map(OrderItemRequest::itemId).toList();
        Map<Long, Item> itemMap = itemRepository.findAllById(itemIds)
                .stream().collect(Collectors.toMap(Item::getId, Function.identity()));

        BigDecimal totalPrice = BigDecimal.ZERO;
        for (var itemRequest : request.items()) {
            Item item = itemMap.get(itemRequest.itemId());
            if (item == null) {
                throw new ItemNotFoundException("Item not found with id: " + itemRequest.itemId());
            }
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setItem(item);
            orderItem.setQuantity(itemRequest.quantity());
            order.getItems().add(orderItem);
            totalPrice = totalPrice.add(item.getPrice().multiply(BigDecimal.valueOf(itemRequest.quantity())));
        }

        order.setTotalPrice(totalPrice);
        Order saved = orderRepository.save(order);
        eventPublisher.publishEvent(new CreateOrderEvent(
                saved.getId().toString(), callerUserId.toString(), saved.getTotalPrice()));
        return toOrderWithUserDto(saved);
    }

    @Override
    @Transactional
    public OrderWithUserDto update(Long id, UpdateOrderRequest request, Long callerUserId, boolean admin) {
        Order order = findOrderOrThrow(id);
        checkOwnershipOrThrow(order, id, callerUserId, admin);
        order.setStatus(request.status());
        return toOrderWithUserDto(order);
    }

    @Override
    @Transactional
    public void delete(Long id, Long callerUserId, boolean admin) {
        Order order = findOrderOrThrow(id);
        checkOwnershipOrThrow(order, id, callerUserId, admin);
        order.setDeleted(true);
    }

    private void checkOwnershipOrThrow(Order order, Long orderId, Long callerUserId, boolean admin) {
        if (admin) {
            return;
        }
        UserDto caller = userServiceClient.getUserById(callerUserId);
        String callerEmail = caller != null ? caller.email() : null;
        if (!order.getUserEmail().equals(callerEmail)) {
            throw new OrderAccessDeniedException("Access denied to order " + orderId);
        }
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
