package com.innowise.orderservice.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final PaymentEventHandler paymentEventHandler;

    @KafkaListener(topics = "payment-events", groupId = "orderservice")
    public void listen(PaymentCompletedEvent event) {
        paymentEventHandler.handle(event);
    }
}
