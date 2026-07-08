package com.innowise.orderservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes {@link CreateOrderEvent} to Kafka only after the enclosing order-creation transaction
 * has committed ({@link TransactionPhase#AFTER_COMMIT}) — publishing inside the transaction risks
 * sending the event before the order row is actually visible, or sending one that gets rolled
 * back. A publish failure is logged and swallowed rather than propagated: the order has already
 * been created successfully by this point, and Payment Service's consumer is idempotent
 * (paymentservice task-01/FIX-01), so a lost notification only leaves the order stuck
 * {@code PENDING} rather than corrupting state — no outbox is needed here (YAGNI, see
 * paymentservice/plans/fix-03-order-producer-create-order.md).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private static final String TOPIC = "order-events";

    private final KafkaTemplate<String, CreateOrderEvent> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(CreateOrderEvent event) {
        try {
            kafkaTemplate.send(TOPIC, event.orderId(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("Failed to publish CREATE_ORDER for orderId={}", event.orderId(), ex);
                        }
                    });
        } catch (Exception ex) {
            log.warn("Failed to publish CREATE_ORDER for orderId={}", event.orderId(), ex);
        }
    }
}
