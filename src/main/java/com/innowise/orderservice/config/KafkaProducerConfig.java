package com.innowise.orderservice.config;

import com.innowise.orderservice.kafka.CreateOrderEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Strongly-typed {@code KafkaTemplate<String, CreateOrderEvent>} built on Spring Boot's
 * auto-configured {@link ProducerFactory} (serializers/bootstrap-servers come from
 * {@code application.yaml}'s {@code spring.kafka.*}). Mirrors paymentservice's
 * {@code config.KafkaProducerConfig} — see that class's Javadoc for why this relies on there
 * being exactly one auto-configured {@code ProducerFactory} bean in the context.
 */
@Configuration
public class KafkaProducerConfig {

    @Bean
    KafkaTemplate<String, CreateOrderEvent> kafkaTemplate(
            ProducerFactory<String, CreateOrderEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
