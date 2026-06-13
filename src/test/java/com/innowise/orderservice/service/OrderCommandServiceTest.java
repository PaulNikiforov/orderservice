package com.innowise.orderservice.service;

import com.innowise.orderservice.client.UserServiceClient;
import com.innowise.orderservice.dto.CreateOrderRequest;
import com.innowise.orderservice.dto.OrderDto;
import com.innowise.orderservice.dto.OrderItemRequest;
import com.innowise.orderservice.dto.OrderWithUserDto;
import com.innowise.orderservice.dto.UpdateOrderRequest;
import com.innowise.orderservice.exception.ItemNotFoundException;
import com.innowise.orderservice.exception.OrderNotFoundException;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.model.Item;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderStatus;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import com.innowise.orderservice.service.impl.OrderServiceImpl;
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
    private OrderServiceImpl orderService;

    private static final OrderDto DUMMY_DTO =
            new OrderDto(1L, 42L, OrderStatus.PENDING, new BigDecimal("0.00"), List.of(), null, null);
    private static final OrderWithUserDto DUMMY_WITH_USER =
            new OrderWithUserDto(1L, OrderStatus.PENDING, new BigDecimal("0.00"), List.of(), null, null, null);

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
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderMapper.toDto(any(Order.class))).thenReturn(DUMMY_DTO);

        var request = new CreateOrderRequest(42L, List.of(new OrderItemRequest(1L, 3)));
        orderService.create(request);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getTotalPrice()).isEqualByComparingTo("30.00");
    }

    @Test
    void create_setsStatusPending() {
        Item item = item(1L, new BigDecimal("5.00"));
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderMapper.toDto(any(Order.class))).thenReturn(DUMMY_DTO);

        orderService.create(new CreateOrderRequest(1L, List.of(new OrderItemRequest(1L, 1))));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void create_throwsItemNotFoundException_whenItemNotExists() {
        when(itemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.create(
                new CreateOrderRequest(1L, List.of(new OrderItemRequest(99L, 1)))))
                .isInstanceOf(ItemNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void create_createsOrderItemsForEachRequest() {
        Item item1 = item(1L, new BigDecimal("10.00"));
        Item item2 = item(2L, new BigDecimal("20.00"));
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item1));
        when(itemRepository.findById(2L)).thenReturn(Optional.of(item2));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderMapper.toDto(any(Order.class))).thenReturn(DUMMY_DTO);

        var request = new CreateOrderRequest(1L, List.of(
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
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderMapper.toWithUserDto(any(Order.class), any())).thenReturn(DUMMY_WITH_USER);

        orderService.update(1L, new UpdateOrderRequest(OrderStatus.CONFIRMED));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void update_throwsOrderNotFoundException_whenNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.update(99L, new UpdateOrderRequest(OrderStatus.CONFIRMED)))
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
