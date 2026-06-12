package com.innowise.orderservice.service;

import com.innowise.orderservice.client.UserServiceClient;
import com.innowise.orderservice.dto.OrderWithUserDto;
import com.innowise.orderservice.dto.UserDto;
import com.innowise.orderservice.exception.OrderNotFoundException;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderStatus;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import com.innowise.orderservice.repository.specification.OrderFilterRequest;
import com.innowise.orderservice.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Order order(Long userId, OrderStatus status) {
        Order order = new Order();
        order.setUserId(userId);
        order.setStatus(status);
        order.setTotalPrice(new BigDecimal("100.00"));
        return order;
    }

    private OrderWithUserDto orderWithUserDto(Long userId, OrderStatus status) {
        var user = new UserDto(userId, "user@example.com", "John", "Doe");
        return new OrderWithUserDto(null, status, new BigDecimal("100.00"), List.of(), null, null, user);
    }

    private UserDto stubUser(Long id) {
        return new UserDto(id, "user@example.com", "John", "Doe");
    }

    @Test
    void getById_returnsOrderWithUser() {
        Order order = order(42L, OrderStatus.PENDING);
        var expected = orderWithUserDto(42L, OrderStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(userServiceClient.getUserById(42L)).thenReturn(stubUser(42L));
        when(orderMapper.toWithUserDto(any(), any())).thenReturn(expected);

        var result = orderService.getById(1L);

        assertThat(result).isEqualTo(expected);
        assertThat(result.user().id()).isEqualTo(42L);
    }

    @Test
    void getById_throwsOrderNotFoundException_whenNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getById(99L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getAll_returnsPaginatedResult() {
        var orders = List.of(order(1L, OrderStatus.PENDING), order(2L, OrderStatus.CONFIRMED));
        var page = new PageImpl<>(orders, PageRequest.of(0, 10), 2);
        when(orderRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
        when(userServiceClient.getUserById(anyLong())).thenReturn(stubUser(1L));
        when(orderMapper.toWithUserDto(any(), any()))
                .thenReturn(orderWithUserDto(1L, OrderStatus.PENDING))
                .thenReturn(orderWithUserDto(2L, OrderStatus.CONFIRMED));

        var filter = new OrderFilterRequest(null, null, null, null);
        var result = orderService.getAll(filter, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void getAll_filtersBy_status() {
        var orders = List.of(order(1L, OrderStatus.CONFIRMED));
        var page = new PageImpl<>(orders, PageRequest.of(0, 10), 1);
        when(orderRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
        when(userServiceClient.getUserById(anyLong())).thenReturn(stubUser(1L));
        when(orderMapper.toWithUserDto(any(), any())).thenReturn(orderWithUserDto(1L, OrderStatus.CONFIRMED));

        var filter = new OrderFilterRequest(null, OrderStatus.CONFIRMED, null, null);
        var result = orderService.getAll(filter, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void getAll_filtersBy_dateRange() {
        var now = LocalDateTime.now();
        var orders = List.of(order(1L, OrderStatus.PENDING));
        var page = new PageImpl<>(orders, PageRequest.of(0, 10), 1);
        when(orderRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
        when(userServiceClient.getUserById(anyLong())).thenReturn(stubUser(1L));
        when(orderMapper.toWithUserDto(any(), any())).thenReturn(orderWithUserDto(1L, OrderStatus.PENDING));

        var filter = new OrderFilterRequest(null, null, now.minusDays(1), now.plusDays(1));
        var result = orderService.getAll(filter, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void getAll_returnsEmpty_whenNoMatch() {
        var page = new PageImpl<Order>(List.of(), PageRequest.of(0, 10), 0);
        when(orderRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

        var filter = new OrderFilterRequest(null, OrderStatus.CANCELLED, null, null);
        var result = orderService.getAll(filter, PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void getByUserId_returnsAllUserOrders() {
        var orders = List.of(order(5L, OrderStatus.PENDING), order(5L, OrderStatus.CONFIRMED));
        when(orderRepository.findAll(any(Specification.class))).thenReturn(orders);
        when(userServiceClient.getUserById(5L)).thenReturn(stubUser(5L));
        when(orderMapper.toWithUserDto(any(), any()))
                .thenReturn(orderWithUserDto(5L, OrderStatus.PENDING))
                .thenReturn(orderWithUserDto(5L, OrderStatus.CONFIRMED));

        var result = orderService.getByUserId(5L);

        assertThat(result).hasSize(2);
        verify(userServiceClient, times(1)).getUserById(5L);
    }

    @Test
    void getByUserId_returnsEmpty_whenNoOrders() {
        when(orderRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(userServiceClient.getUserById(999L)).thenReturn(stubUser(999L));

        var result = orderService.getByUserId(999L);

        assertThat(result).isEmpty();
        verify(userServiceClient, times(1)).getUserById(999L);
    }
}
