package com.innowise.orderservice.kafka;

import com.innowise.orderservice.model.OrderOutboxEvent;
import com.innowise.orderservice.repository.OrderOutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class OrderEventOutboxPublisher {

    static final String TOPIC = "order-events";
    private static final long SEND_TIMEOUT_MS = 5000;

    private final OrderOutboxRepository orderOutboxRepository;
    private final KafkaTemplate<String, CreateOrderEvent> kafkaTemplate;

    @Value("${order.outbox.batch-size:50}")
    private int batchSize = 50;

    public OrderEventOutboxPublisher(OrderOutboxRepository orderOutboxRepository,
                                      KafkaTemplate<String, CreateOrderEvent> kafkaTemplate) {
        this.orderOutboxRepository = orderOutboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${order.outbox.poll-interval-ms:2000}")
    public void publishPending() {
        Page<OrderOutboxEvent> pending = orderOutboxRepository.findByPublishedFalse(PageRequest.of(0, batchSize));
        for (OrderOutboxEvent event : pending) {
            publishOne(event);
        }
    }

    private void publishOne(OrderOutboxEvent event) {
        String orderId = event.getOrderId().toString();
        try {
            kafkaTemplate.send(TOPIC, orderId, new CreateOrderEvent(orderId, event.getUserId(), event.getAmount()))
                    .get(SEND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException e) {
            log.warn("Failed to publish CREATE_ORDER for orderId={}; will retry on the next poll tick",
                    orderId, e);
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while publishing CREATE_ORDER for orderId={}; will retry on the next poll tick",
                    orderId, e);
            return;
        }
        event.setPublished(true);
        orderOutboxRepository.save(event);
    }
}
