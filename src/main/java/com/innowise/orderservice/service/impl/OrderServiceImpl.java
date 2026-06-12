package com.innowise.orderservice.service.impl;

import com.innowise.orderservice.dto.CreateOrderRequest;
import com.innowise.orderservice.dto.OrderDto;
import com.innowise.orderservice.dto.UpdateOrderRequest;
import com.innowise.orderservice.exception.ItemNotFoundException;
import com.innowise.orderservice.exception.OrderNotFoundException;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.model.Item;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderItem;
import com.innowise.orderservice.model.OrderStatus;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import com.innowise.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ItemRepository itemRepository;
    private final OrderMapper orderMapper;

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
    public OrderDto update(Long id, UpdateOrderRequest request) {
        Order order = findOrderOrThrow(id);
        order.setStatus(request.status());
        return orderMapper.toDto(order);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Order order = findOrderOrThrow(id);
        order.setDeleted(true);
    }

    private Order findOrderOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + id));
    }
}
