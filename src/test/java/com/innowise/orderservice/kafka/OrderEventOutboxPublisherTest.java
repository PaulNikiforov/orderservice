package com.innowise.orderservice.kafka;

import com.innowise.orderservice.model.OrderOutboxEvent;
import com.innowise.orderservice.repository.OrderOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEventOutboxPublisherTest {

    private static final String TOPIC = "order-events";

    @Mock
    private OrderOutboxRepository orderOutboxRepository;

    @Mock
    private KafkaTemplate<String, CreateOrderEvent> kafkaTemplate;

    private OrderEventOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OrderEventOutboxPublisher(orderOutboxRepository, kafkaTemplate);
    }

    @Test
    @DisplayName("happy path: successful send marks published=true and persists, send before save")
    void publishPending_whenSendSucceeds_marksPublishedAndSaves() {
        OrderOutboxEvent event = pending(7L, "42", new BigDecimal("30.00"));
        stubPage(event);

        when(kafkaTemplate.send(eq(TOPIC), eq("7"), any(CreateOrderEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult()));

        publisher.publishPending();

        assertThat(event.isPublished()).isTrue();
        ArgumentCaptor<CreateOrderEvent> eventCaptor = ArgumentCaptor.forClass(CreateOrderEvent.class);
        InOrder order = inOrder(kafkaTemplate, orderOutboxRepository);
        order.verify(kafkaTemplate).send(eq(TOPIC), eq("7"), eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isEqualTo(new CreateOrderEvent("7", "42", new BigDecimal("30.00")));
        order.verify(orderOutboxRepository).save(event);
    }

    @Test
    @DisplayName("failure path: send fails -> published stays false and row is not saved")
    void publishPending_whenSendFails_leavesPublishedFalseAndDoesNotSave() {
        OrderOutboxEvent event = pending(7L, "42", new BigDecimal("30.00"));
        stubPage(event);

        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka broker down")));

        publisher.publishPending();

        assertThat(event.isPublished()).isFalse();
        verify(orderOutboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("polls only unpublished rows in batches of 50")
    void publishPending_pollsUnpublishedRowsInBatchesOfFifty() {
        when(orderOutboxRepository.findByPublishedFalse(any())).thenReturn(emptyPage());

        publisher.publishPending();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(orderOutboxRepository).findByPublishedFalse(pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getPageNumber()).isZero();
    }

    private void stubPage(OrderOutboxEvent event) {
        when(orderOutboxRepository.findByPublishedFalse(any()))
                .thenReturn(new PageImpl<>(List.of(event), PageRequest.of(0, 50), 1));
    }

    private Page<OrderOutboxEvent> emptyPage() {
        return new PageImpl<>(List.of(), PageRequest.of(0, 50), 0);
    }

    private OrderOutboxEvent pending(Long orderId, String userId, BigDecimal amount) {
        return new OrderOutboxEvent(orderId, userId, amount);
    }

    private static SendResult<String, CreateOrderEvent> sendResult() {
        return new SendResult<>(null, null);
    }
}
