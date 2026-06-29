package com.innowise.orderservice.service;

import com.innowise.orderservice.client.UserServiceClient;
import com.innowise.orderservice.exception.ItemNotFoundException;
import com.innowise.orderservice.exception.OrderNotFoundException;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.model.Item;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderStatus;
import com.innowise.orderservice.model.dto.CreateOrderRequest;
import com.innowise.orderservice.model.dto.OrderItemRequest;
import com.innowise.orderservice.model.dto.OrderWithUserDto;
import com.innowise.orderservice.model.dto.UpdateOrderRequest;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import com.innowise.orderservice.service.impl.OrderCommandServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCommandServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private OrderCommandServiceImpl orderService;

    private Item item(Long id, BigDecimal price) {
        Item item = new Item();
        item.setId(id);
        item.setName("Item " + id);
        item.setPrice(price);
        return item;
    }

    @Test
    void create_savesOrderWithCorrectTotalPrice() {
        Item item = item(1L, new BigDecimal("10.00"));
        when(itemRepository.findAllById(List.of(1L))).thenReturn(List.of(item));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var request = new CreateOrderRequest("user@test.com", List.of(new OrderItemRequest(1L, 3)));
        orderService.create(request);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getTotalPrice()).isEqualByComparingTo("30.00");
    }

    @Test
    void create_setsStatusPending() {
        Item item = item(1L, new BigDecimal("5.00"));
        when(itemRepository.findAllById(List.of(1L))).thenReturn(List.of(item));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderService.create(new CreateOrderRequest("user@test.com", List.of(new OrderItemRequest(1L, 1))));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void create_setsUserEmail() {
        Item item = item(1L, new BigDecimal("5.00"));
        when(itemRepository.findAllById(List.of(1L))).thenReturn(List.of(item));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderService.create(new CreateOrderRequest("alice@test.com", List.of(new OrderItemRequest(1L, 1))));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getUserEmail()).isEqualTo("alice@test.com");
    }

    @Test
    void create_throwsItemNotFoundException_whenItemNotExists() {
        when(itemRepository.findAllById(List.of(99L))).thenReturn(List.of());

        var request = new CreateOrderRequest("user@test.com", List.of(new OrderItemRequest(99L, 1)));
        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(ItemNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void create_createsOrderItemsForEachRequest() {
        Item item1 = item(1L, new BigDecimal("10.00"));
        Item item2 = item(2L, new BigDecimal("20.00"));
        when(itemRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(item1, item2));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var request = new CreateOrderRequest("user@test.com", List.of(
                new OrderItemRequest(1L, 2),
                new OrderItemRequest(2L, 1)
        ));
        orderService.create(request);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getItems()).hasSize(2);
    }

    @Test
    void update_changesStatus() {
        Order order = new Order();
        order.setStatus(OrderStatus.PENDING);
        order.setUserEmail("user@test.com");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderMapper.toWithUserDto(any(Order.class), any())).thenReturn(
                new OrderWithUserDto(1L, OrderStatus.CONFIRMED, new BigDecimal("0.00"), List.of(), null, null, null));

        orderService.update(1L, new UpdateOrderRequest(OrderStatus.CONFIRMED));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void update_throwsOrderNotFoundException_whenNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        var request = new UpdateOrderRequest(OrderStatus.CONFIRMED);
        assertThatThrownBy(() -> orderService.update(99L, request))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void delete_setDeletedTrue() {
        Order order = new Order();
        order.setDeleted(false);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        orderService.delete(1L);

        assertThat(order.getDeleted()).isTrue();
        verify(orderRepository, never()).save(any());
    }

    @Test
    void delete_throwsOrderNotFoundException_whenNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.delete(99L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("99");
    }
}
