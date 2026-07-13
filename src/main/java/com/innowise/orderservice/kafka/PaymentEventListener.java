package com.innowise.orderservice.kafka;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    static final String TOPIC = "payment-events";

    private final PaymentEventHandler paymentEventHandler;
    private final Validator validator;

    @KafkaListener(topics = TOPIC, groupId = "orderservice")
    public void listen(PaymentCompletedEvent event) {
        try {
            Set<ConstraintViolation<PaymentCompletedEvent>> violations = validator.validate(event);
            if (!violations.isEmpty()) {
                throw new ConstraintViolationException(violations);
            }
            paymentEventHandler.handle(event);
        } catch (Exception ex) {
            log.error("Failed to process payment event for orderId={}, status={}",
                    event.orderId(), event.status(), ex);
            throw ex;
        }
    }
}
