package com.innowise.orderservice.service.impl;

import com.innowise.orderservice.client.UserServiceClient;
import com.innowise.orderservice.dto.CreateOrderRequest;
import com.innowise.orderservice.dto.OrderDto;
import com.innowise.orderservice.dto.OrderWithUserDto;
import com.innowise.orderservice.dto.UpdateOrderRequest;
import com.innowise.orderservice.dto.UserDto;
import com.innowise.orderservice.exception.ItemNotFoundException;
import com.innowise.orderservice.exception.OrderNotFoundException;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.model.Item;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderItem;
import com.innowise.orderservice.model.OrderStatus;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import com.innowise.orderservice.repository.specification.OrderFilterRequest;
import com.innowise.orderservice.repository.specification.OrderSpecification;
import com.innowise.orderservice.service.OrderCommandService;
import com.innowise.orderservice.service.OrderQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderCommandService, OrderQueryService {

    private final OrderRepository orderRepository;
    private final ItemRepository itemRepository;
    private final OrderMapper orderMapper;
    private final UserServiceClient userServiceClient;

    @Override
    @Transactional
    public OrderDto create(CreateOrderRequest request) {
        Order order = new Order();
        order.setUserId(request.userId());
        order.setStatus(OrderStatus.PENDING);

        BigDecimal totalPrice = BigDecimal.ZERO;

        for (var itemRequest : request.items()) {
            Item item = itemRepository.findById(itemRequest.itemId())
                    .orElseThrow(() -> new ItemNotFoundException(
                            "Item not found with id: " + itemRequest.itemId()));

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setItem(item);
            orderItem.setQuantity(itemRequest.quantity());

            order.getItems().add(orderItem);
            totalPrice = totalPrice.add(item.getPrice().multiply(
                    BigDecimal.valueOf(itemRequest.quantity())));
        }

        order.setTotalPrice(totalPrice);
        return orderMapper.toDto(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderWithUserDto update(Long id, UpdateOrderRequest request) {
        Order order = findOrderOrThrow(id);
        order.setStatus(request.status());
        return enrichWithUser(order);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Order order = findOrderOrThrow(id);
        order.setDeleted(true);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderWithUserDto getById(Long id) {
        return enrichWithUser(findOrderOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderWithUserDto> getAll(OrderFilterRequest filter, Pageable pageable) {
        Page<Order> page = orderRepository.findAll(OrderSpecification.fromFilter(filter), pageable);
        // HashMap allows null values — getUserById returns null when UserService is unavailable (graceful degradation)
        Map<Long, UserDto> userCache = new HashMap<>();
        page.getContent().stream()
                .map(Order::getUserId)
                .distinct()
                .forEach(uid -> userCache.put(uid, userServiceClient.getUserById(uid)));
        return page.map(order -> orderMapper.toWithUserDto(order, userCache.get(order.getUserId())));
    }

    private OrderWithUserDto enrichWithUser(Order order) {
        UserDto user = userServiceClient.getUserById(order.getUserId());
        return orderMapper.toWithUserDto(order, user);
    }

    private Order findOrderOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + id));
    }
}
