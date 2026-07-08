package com.innowise.orderservice.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTest {

    @Mock
    private KafkaTemplate<String, CreateOrderEvent> kafkaTemplate;

    @Test
    void onOrderCreated_swallowsFailure_whenKafkaSendFails() {
        CreateOrderEvent event = new CreateOrderEvent("1", "2", BigDecimal.TEN);
        CompletableFuture<SendResult<String, CreateOrderEvent>> failed = CompletableFuture.failedFuture(
                new KafkaException("broker unreachable"));
        when(kafkaTemplate.send(OrderEventPublisher.TOPIC, event.orderId(), event)).thenReturn(failed);

        OrderEventPublisher publisher = new OrderEventPublisher(kafkaTemplate);

        assertThatCode(() -> publisher.onOrderCreated(event)).doesNotThrowAnyException();

        verify(kafkaTemplate).send(OrderEventPublisher.TOPIC, event.orderId(), event);
    }

    @Test
    void onOrderCreated_sendsToOrderEventsTopic_whenPublished() {
        CreateOrderEvent event = new CreateOrderEvent("1", "2", BigDecimal.TEN);
        when(kafkaTemplate.send(OrderEventPublisher.TOPIC, event.orderId(), event))
                .thenReturn(CompletableFuture.completedFuture(
                        new SendResult<>(new ProducerRecord<>(OrderEventPublisher.TOPIC, event.orderId(), event), null)));

        OrderEventPublisher publisher = new OrderEventPublisher(kafkaTemplate);

        assertThatCode(() -> publisher.onOrderCreated(event)).doesNotThrowAnyException();

        verify(kafkaTemplate).send(OrderEventPublisher.TOPIC, event.orderId(), event);
    }
}
