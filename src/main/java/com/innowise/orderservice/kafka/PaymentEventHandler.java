package com.innowise.orderservice.kafka;

import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderStatus;
import com.innowise.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventHandler {

    private final OrderRepository orderRepository;

    public void handle(PaymentCompletedEvent event) {
        Long orderId;
        try {
            orderId = Long.parseLong(event.orderId());
        } catch (NumberFormatException e) {
            log.warn("Received payment event with non-parseable orderId: {}", event.orderId());
            return;
        }

        if (event.status() == PaymentStatus.SUCCESS) {
            orderRepository.findById(orderId).ifPresent(order -> {
                if (order.getStatus() != OrderStatus.PAID) {
                    order.setStatus(OrderStatus.PAID);
                    orderRepository.save(order);
                }
            });
        } else if (event.status() == PaymentStatus.FAILED) {
            orderRepository.findById(orderId).ifPresent(order -> {
                if (order.getStatus() != OrderStatus.CANCELLED) {
                    order.setStatus(OrderStatus.CANCELLED);
                    orderRepository.save(order);
                }
            });
        }
    }
}
