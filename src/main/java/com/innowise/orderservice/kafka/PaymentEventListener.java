package com.innowise.orderservice.kafka;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * {@link PaymentCompletedEvent}'s bean-validation constraints are checked explicitly against the
 * injected {@link Validator} rather than via {@code @Validated}/{@code @Valid} on the listener
 * method: method-level validation relies on a CGLIB proxy intercepting the call, and
 * {@code @KafkaListener} invocation ordering does not reliably go through that proxy, so the
 * constraint would silently never fire (confirmed in practice on paymentservice's
 * {@code OrderEventListener}, FIX-01). A violation throws
 * {@link jakarta.validation.ConstraintViolationException}, which {@link
 * com.innowise.orderservice.config.KafkaConsumerConfig}'s error handler treats the same as any
 * other processing failure (bounded retry, then DLT).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final PaymentEventHandler paymentEventHandler;
    private final Validator validator;

    @KafkaListener(topics = "payment-events", groupId = "orderservice")
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
