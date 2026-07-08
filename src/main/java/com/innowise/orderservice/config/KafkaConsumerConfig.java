package com.innowise.orderservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Resilience policy for {@code @KafkaListener} consumers: a bounded exponential backoff, then a
 * dead-letter publish instead of the container's default behavior of committing the offset and
 * silently dropping a permanently-failing record. Applies to both deserialization failures
 * (surfaced by {@code ErrorHandlingDeserializer}, configured in {@code application.yaml}) and
 * exceptions thrown from listener methods — this is the sole safety net for
 * {@link com.innowise.orderservice.kafka.PaymentEventListener} (FIX-01). Mirrors paymentservice's
 * {@code config.KafkaConsumerConfig}.
 *
 * <p>Declaring a {@link CommonErrorHandler} bean is picked up automatically by Spring Boot's
 * auto-configured {@code ConcurrentKafkaListenerContainerFactory} — no factory customization
 * needed.
 */
@Configuration
public class KafkaConsumerConfig {

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_INTERVAL_MS = 1000L;
    private static final long MAX_INTERVAL_MS = 10_000L;
    private static final double MULTIPLIER = 2.0;

    @Bean
    KafkaTemplate<Object, Object> deadLetterKafkaTemplate(ProducerFactory<Object, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    CommonErrorHandler kafkaConsumerErrorHandler(KafkaTemplate<Object, Object> deadLetterKafkaTemplate) {
        var recoverer = new DeadLetterPublishingRecoverer(deadLetterKafkaTemplate);
        var backOff = new ExponentialBackOffWithMaxRetries(MAX_RETRIES);
        backOff.setInitialInterval(INITIAL_INTERVAL_MS);
        backOff.setMultiplier(MULTIPLIER);
        backOff.setMaxInterval(MAX_INTERVAL_MS);
        return new DefaultErrorHandler(recoverer, backOff);
    }
}
