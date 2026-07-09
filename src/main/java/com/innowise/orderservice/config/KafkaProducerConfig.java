package com.innowise.orderservice.config;

import com.innowise.orderservice.kafka.CreateOrderEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
public class KafkaProducerConfig {

    @Bean
    KafkaTemplate<String, CreateOrderEvent> kafkaTemplate(
            ProducerFactory<String, CreateOrderEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
