package com.innowise.orderservice.kafka;

import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderStatus;
import com.innowise.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentEventHandlerTest {

    @Mock
    private OrderRepository orderRepository;

    @Test
    void handle_shouldSetOrderStatusToPaid_whenPaymentSucceededForExistingOrder() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(OrderStatus.PENDING);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        PaymentEventHandler handler = new PaymentEventHandler(orderRepository);
        PaymentCompletedEvent event = new PaymentCompletedEvent("1", PaymentStatus.SUCCESS);

        handler.handle(event);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());

        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void handle_shouldSetOrderStatusToCancelled_whenPaymentFailedForExistingOrder() {
        Order order = new Order();
        order.setId(2L);
        order.setStatus(OrderStatus.PENDING);

        when(orderRepository.findById(2L)).thenReturn(Optional.of(order));

        PaymentEventHandler handler = new PaymentEventHandler(orderRepository);
        PaymentCompletedEvent event = new PaymentCompletedEvent("2", PaymentStatus.FAILED);

        handler.handle(event);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());

        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void handle_shouldDoNothing_whenOrderNotFound() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        PaymentEventHandler handler = new PaymentEventHandler(orderRepository);
        PaymentCompletedEvent event = new PaymentCompletedEvent("999", PaymentStatus.SUCCESS);

        assertThatCode(() -> handler.handle(event)).doesNotThrowAnyException();

        verify(orderRepository, never()).save(any());
    }

    @Test
    void handle_shouldNotSaveAgain_whenOrderAlreadyInTargetStatus() {
        Order order = new Order();
        order.setId(3L);
        order.setStatus(OrderStatus.PAID);

        when(orderRepository.findById(3L)).thenReturn(Optional.of(order));

        PaymentEventHandler handler = new PaymentEventHandler(orderRepository);
        PaymentCompletedEvent event = new PaymentCompletedEvent("3", PaymentStatus.SUCCESS);

        handler.handle(event);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void handle_shouldDoNothing_whenOrderIdIsNotParseable() {
        PaymentEventHandler handler = new PaymentEventHandler(orderRepository);
        PaymentCompletedEvent event = new PaymentCompletedEvent("not-a-number", PaymentStatus.SUCCESS);

        assertThatCode(() -> handler.handle(event)).doesNotThrowAnyException();

        verifyNoInteractions(orderRepository);
    }
}
