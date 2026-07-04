package com.innowise.orderservice.kafka;

import com.innowise.orderservice.TestcontainersConfiguration;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderStatus;
import com.innowise.orderservice.repository.OrderRepository;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.kafka.KafkaContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PaymentEventListenerIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KafkaContainer kafkaContainer;

    private KafkaProducer<String, String> producer;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE order_items, orders, items RESTART IDENTITY CASCADE");

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producer = new KafkaProducer<>(props);
    }

    @AfterEach
    void tearDown() {
        if (producer != null) {
            producer.close();
        }
    }

    @Test
    void paymentSuccessEvent_transitionsOrderToPaid() throws Exception {
        Order order = new Order();
        order.setUserEmail("payment-test@test.com");
        order.setStatus(OrderStatus.PENDING);
        order.setTotalPrice(new BigDecimal("42.00"));
        order.setDeleted(false);
        Long orderId = orderRepository.save(order).getId();

        String json = """
                {"orderId":"%s","status":"SUCCESS"}
                """.formatted(orderId);

        producer.send(new ProducerRecord<>("payment-events", null, json)).get();
        producer.flush();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(orderRepository.findById(orderId))
                        .isPresent()
                        .get()
                        .extracting(Order::getStatus)
                        .isEqualTo(OrderStatus.PAID));

        producer.send(new ProducerRecord<>("payment-events", null, json)).get();
        producer.flush();

        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(orderRepository.findById(orderId))
                        .isPresent()
                        .get()
                        .extracting(Order::getStatus)
                        .isEqualTo(OrderStatus.PAID));
    }
}
