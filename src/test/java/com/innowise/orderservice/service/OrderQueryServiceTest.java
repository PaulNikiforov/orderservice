package com.innowise.orderservice.service;

import com.innowise.orderservice.client.UserServiceClient;
import com.innowise.orderservice.exception.OrderAccessDeniedException;
import com.innowise.orderservice.exception.OrderNotFoundException;
import com.innowise.orderservice.exception.UserServiceUnavailableException;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

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
        when(userServiceClient.getUserById(10L)).thenReturn(new UserDto(10L, "alice@test.com", "Alice", "A"));
        when(orderMapper.toWithUserDto(any(), any())).thenReturn(expected);

        var result = orderService.getById(1L, 10L, false);

        assertThat(result).isEqualTo(expected);
        assertThat(result.user().email()).isEqualTo("alice@test.com");
    }

    @Test
    void getById_throwsOrderNotFoundException_whenNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getById(99L, 10L, false))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getById_throwsOrderAccessDeniedException_whenUserNotOwner() {
        Order order = order("alice@test.com", OrderStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(userServiceClient.getUserById(99L)).thenReturn(new UserDto(99L, "bob@test.com", "Bob", "B"));

        assertThatThrownBy(() -> orderService.getById(1L, 99L, false))
                .isInstanceOf(OrderAccessDeniedException.class);
    }

    @Test
    void getById_allowsAdmin_evenWhenNotOwner() {
        Order order = order("alice@test.com", OrderStatus.PENDING);
        var expected = orderWithUserDto("alice@test.com", OrderStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderMapper.toWithUserDto(any(), any())).thenReturn(expected);

        var result = orderService.getById(1L, 99L, true);

        assertThat(result).isEqualTo(expected);
        verify(userServiceClient, never()).getUserById(any());
    }

    @Test
    void getById_propagatesUserServiceUnavailableException_whenResolvingCallerFails() {
        Order order = order("alice@test.com", OrderStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(userServiceClient.getUserById(10L))
                .thenThrow(new UserServiceUnavailableException("down", new RuntimeException()));

        assertThatThrownBy(() -> orderService.getById(1L, 10L, false))
                .isInstanceOf(UserServiceUnavailableException.class);
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
        var result = orderService.getAll(filter, 5L, true, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void getAll_returnsEmpty_whenNoMatch() {
        var page = new PageImpl<Order>(List.of(), PageRequest.of(0, 10), 0);
        when(orderRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

        var filter = new OrderFilterRequest(null, List.of(OrderStatus.CANCELLED), null, null);
        var result = orderService.getAll(filter, 5L, true, PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void getAll_forcesOwnEmail_whenUserNotAdmin() {
        when(userServiceClient.getUserById(5L)).thenReturn(new UserDto(5L, "own@test.com", "Own", "O"));
        var page = new PageImpl<Order>(List.of(), PageRequest.of(0, 10), 0);
        when(orderRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

        var filter = new OrderFilterRequest("someone-else@test.com", List.of(), null, null);
        orderService.getAll(filter, 5L, false, PageRequest.of(0, 10));

        verify(userServiceClient).getUserById(5L);
        verify(orderRepository).findAll(any(Specification.class), any(PageRequest.class));
    }

    @Test
    void getAll_admin_usesClientProvidedEmail() {
        var page = new PageImpl<Order>(List.of(), PageRequest.of(0, 10), 0);
        when(orderRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

        var filter = new OrderFilterRequest("alice@test.com", List.of(), null, null);
        orderService.getAll(filter, 5L, true, PageRequest.of(0, 10));

        verify(userServiceClient, never()).getUserById(any());
    }

    @Test
    void getAll_admin_omittedEmailReturnsAll() {
        var page = new PageImpl<Order>(List.of(), PageRequest.of(0, 10), 0);
        when(orderRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

        var filter = new OrderFilterRequest(null, List.of(), null, null);
        orderService.getAll(filter, 5L, true, PageRequest.of(0, 10));

        verify(userServiceClient, never()).getUserById(any());
    }

    @Test
    void getAll_propagatesUserServiceUnavailableException_whenResolvingCallerFails() {
        when(userServiceClient.getUserById(5L))
                .thenThrow(new UserServiceUnavailableException("down", new RuntimeException()));

        var filter = new OrderFilterRequest(null, List.of(), null, null);

        assertThatThrownBy(() -> orderService.getAll(filter, 5L, false, PageRequest.of(0, 10)))
                .isInstanceOf(UserServiceUnavailableException.class);

        verify(orderRepository, never()).findAll(any(Specification.class), any(PageRequest.class));
    }
}
