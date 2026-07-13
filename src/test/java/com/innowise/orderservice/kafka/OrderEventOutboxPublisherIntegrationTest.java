package com.innowise.orderservice.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.innowise.orderservice.StubJwksUri;
import com.innowise.orderservice.TestcontainersConfiguration;
import com.innowise.orderservice.model.Item;
import com.innowise.orderservice.model.dto.CreateOrderRequest;
import com.innowise.orderservice.model.dto.OrderItemRequest;
import com.innowise.orderservice.repository.ItemRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.kafka.KafkaContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@StubJwksUri
class OrderEventOutboxPublisherIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final String USER_EMAIL = "user@test.com";
    private static final String TOPIC = OrderEventOutboxPublisher.TOPIC;

    static final WireMockServer wireMockServer;

    static {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("user-service.url", () -> "http://localhost:" + wireMockServer.port());
        registry.add("order.outbox.poll-interval-ms", () -> "200");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaContainer kafkaContainer;

    private KafkaConsumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE order_items, orders, items, order_outbox_events RESTART IDENTITY CASCADE");
        wireMockServer.resetAll();

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-order-events-" + System.nanoTime());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(TOPIC));
        consumer.poll(Duration.ZERO);
        consumer.seekToEnd(consumer.assignment());
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    private void stubUser(Long id, String email) {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/users/" + id))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":%d,"email":"%s","name":"John","surname":"Doe"}
                                """.formatted(id, email))));
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/users/by-email"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":%d,"email":"%s","name":"John","surname":"Doe"}
                                """.formatted(id, email))));
    }

    private ConsumerRecord<String, String> pollOne() {
        ConsumerRecords<String, String>[] holder = new ConsumerRecords[1];
        await().atMost(Duration.ofSeconds(10)).until(() -> {
            holder[0] = consumer.poll(Duration.ofMillis(500));
            return !holder[0].isEmpty();
        });
        return holder[0].iterator().next();
    }

    @Test
    void createOrder_publishesCreateOrderEventViaOutbox() throws Exception {
        Item item = new Item();
        item.setName("Widget");
        item.setPrice(new BigDecimal("15.00"));
        item = itemRepository.save(item);
        stubUser(USER_ID, USER_EMAIL);

        var request = new CreateOrderRequest(List.of(new OrderItemRequest(item.getId(), 2)));
        String body = mockMvc.perform(post("/api/v1/orders")
                        .with(jwt().jwt(j -> j.claim("sub", String.valueOf(USER_ID)).claim("role", "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(body);
        String orderId = created.get("id").asText();

        ConsumerRecord<String, String> consumerRecord = pollOne();
        JsonNode event = objectMapper.readTree(consumerRecord.value());
        assertThat(event.get("orderId").asText()).isEqualTo(orderId);
        assertThat(event.get("userId").asText()).isEqualTo(String.valueOf(USER_ID));
        assertThat(new BigDecimal(event.get("amount").asText())).isEqualByComparingTo("30.00");
    }

    @Test
    void createOrder_publishesNothing_whenItemNotFoundAndTransactionRollsBack() throws Exception {
        stubUser(USER_ID, USER_EMAIL);
        var request = new CreateOrderRequest(List.of(new OrderItemRequest(999999L, 1)));

        mockMvc.perform(post("/api/v1/orders")
                        .with(jwt().jwt(j -> j.claim("sub", String.valueOf(USER_ID)).claim("role", "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());

        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(consumer.poll(Duration.ofMillis(200)).isEmpty()).isTrue());
    }
}
