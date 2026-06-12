package com.innowise.orderservice.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.innowise.orderservice.TestcontainersConfiguration;
import com.innowise.orderservice.dto.CreateOrderRequest;
import com.innowise.orderservice.dto.OrderDto;
import com.innowise.orderservice.dto.OrderItemRequest;
import com.innowise.orderservice.dto.OrderWithUserDto;
import com.innowise.orderservice.model.Item;
import com.innowise.orderservice.model.OrderStatus;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OrderIntegrationTest {

    // Static initializer ensures WireMock is started before @DynamicPropertySource fires
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
    static void overrideUserServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("user-service.url", () -> "http://localhost:" + wireMockServer.port());
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE order_items, orders, items RESTART IDENTITY CASCADE");
        wireMockServer.resetAll();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Item createItem(String name, BigDecimal price) {
        Item item = new Item();
        item.setName(name);
        item.setPrice(price);
        return itemRepository.save(item);
    }

    private void stubUserService(Long userId) {
        wireMockServer.stubFor(get(urlEqualTo("/api/users/" + userId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":%d,"email":"user@test.com","name":"John","surname":"Doe"}
                                """.formatted(userId))));
    }

    private void stubUserServiceDown(Long userId) {
        wireMockServer.stubFor(get(urlEqualTo("/api/users/" + userId))
                .willReturn(aResponse().withStatus(503)));
    }

    // ── POST /api/v1/orders ──────────────────────────────────────────────────

    @Test
    void createOrder_returnsCreatedDto() {
        Item item = createItem("Widget", new BigDecimal("49.99"));

        ResponseEntity<OrderDto> response = restTemplate.postForEntity(
                "/api/v1/orders",
                new CreateOrderRequest(42L, List.of(new OrderItemRequest(item.getId(), 2))),
                OrderDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        OrderDto body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.userId()).isEqualTo(42L);
        assertThat(body.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(body.totalPrice()).isEqualByComparingTo(new BigDecimal("99.98"));
    }

    @Test
    void createOrder_returns404_forUnknownItem() {
        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/v1/orders",
                new CreateOrderRequest(42L, List.of(new OrderItemRequest(999999L, 1))),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── GET /api/v1/orders/{id} ──────────────────────────────────────────────

    @Test
    void getById_persistsToDatabase_andIncludesUserInfo() {
        Item item = createItem("Gadget", new BigDecimal("25.00"));
        OrderDto created = restTemplate.postForEntity(
                "/api/v1/orders",
                new CreateOrderRequest(42L, List.of(new OrderItemRequest(item.getId(), 1))),
                OrderDto.class).getBody();
        assertThat(created).isNotNull();

        stubUserService(42L);

        ResponseEntity<OrderWithUserDto> response = restTemplate.getForEntity(
                "/api/v1/orders/" + created.id(), OrderWithUserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        OrderWithUserDto fetched = response.getBody();
        assertThat(fetched).isNotNull();
        assertThat(fetched.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(fetched.totalPrice()).isGreaterThan(BigDecimal.ZERO);
        assertThat(fetched.user()).isNotNull();
        assertThat(fetched.user().id()).isEqualTo(42L);
        // Verify record exists in DB independently of the REST layer
        assertThat(orderRepository.findById(created.id())).isPresent();
    }

    @Test
    void getById_returnsNullUser_whenUserServiceDown() {
        Item item = createItem("Thing", new BigDecimal("10.00"));
        OrderDto created = restTemplate.postForEntity(
                "/api/v1/orders",
                new CreateOrderRequest(42L, List.of(new OrderItemRequest(item.getId(), 1))),
                OrderDto.class).getBody();
        assertThat(created).isNotNull();

        stubUserServiceDown(42L);

        ResponseEntity<OrderWithUserDto> response = restTemplate.getForEntity(
                "/api/v1/orders/" + created.id(), OrderWithUserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().user()).isNull();
    }

    @Test
    void getById_returns404_whenOrderNotFound() {
        ResponseEntity<Void> response = restTemplate.getForEntity(
                "/api/v1/orders/999999", Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
