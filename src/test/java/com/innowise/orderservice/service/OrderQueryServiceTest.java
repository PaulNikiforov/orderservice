package com.innowise.orderservice.service;

import com.innowise.orderservice.client.UserServiceClient;
import com.innowise.orderservice.exception.OrderNotFoundException;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderStatus;
import com.innowise.orderservice.model.dto.OrderWithUserDto;
import com.innowise.orderservice.model.dto.UserDto;
import com.innowise.orderservice.repository.OrderRepository;
import com.innowise.orderservice.repository.specification.OrderFilterRequest;
import com.innowise.orderservice.service.impl.OrderQueryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private OrderQueryServiceImpl orderService;

    private Order order(String userEmail, OrderStatus status) {
        Order order = new Order();
        order.setUserEmail(userEmail);
        order.setStatus(status);
        order.setTotalPrice(new BigDecimal("100.00"));
        return order;
    }

    private OrderWithUserDto orderWithUserDto(String userEmail, OrderStatus status) {
        var user = new UserDto(null, userEmail, "John", "Doe");
        return new OrderWithUserDto(null, status, new BigDecimal("100.00"), List.of(), null, null, user);
    }

    @Test
    void getById_returnsOrderWithUser() {
        Order order = order("alice@test.com", OrderStatus.PENDING);
        var expected = orderWithUserDto("alice@test.com", OrderStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderMapper.toWithUserDto(any(), any())).thenReturn(expected);

        var result = orderService.getById(1L);

        assertThat(result).isEqualTo(expected);
        assertThat(result.user().email()).isEqualTo("alice@test.com");
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
        var orders = List.of(order("a@test.com", OrderStatus.PENDING), order("b@test.com", OrderStatus.CONFIRMED));
        var page = new PageImpl<>(orders, PageRequest.of(0, 10), 2);
        when(orderRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
        when(orderMapper.toWithUserDto(any(), any()))
                .thenReturn(orderWithUserDto("a@test.com", OrderStatus.PENDING))
                .thenReturn(orderWithUserDto("b@test.com", OrderStatus.CONFIRMED));

        var filter = new OrderFilterRequest(null, List.of(), null, null);
        var result = orderService.getAll(filter, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void getAll_returnsEmpty_whenNoMatch() {
        var page = new PageImpl<Order>(List.of(), PageRequest.of(0, 10), 0);
        when(orderRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

        var filter = new OrderFilterRequest(null, List.of(OrderStatus.CANCELLED), null, null);
        var result = orderService.getAll(filter, PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }
}
