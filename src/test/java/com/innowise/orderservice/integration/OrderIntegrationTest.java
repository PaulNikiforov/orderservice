package com.innowise.orderservice.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.innowise.orderservice.TestcontainersConfiguration;
import com.innowise.orderservice.model.dto.CreateOrderRequest;
import com.innowise.orderservice.model.dto.OrderDto;
import com.innowise.orderservice.model.dto.OrderItemRequest;
import com.innowise.orderservice.model.dto.OrderWithUserDto;
import com.innowise.orderservice.model.dto.UpdateOrderRequest;
import com.innowise.orderservice.model.Item;
import com.innowise.orderservice.model.Order;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OrderIntegrationTest {

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

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE order_items, orders, items RESTART IDENTITY CASCADE");
        wireMockServer.resetAll();
    }

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

    @Test
    void getAll_filtersByStatus() throws Exception {
        stubAnyUser();
        persistOrder(42L, OrderStatus.PENDING);
        persistOrder(42L, OrderStatus.CONFIRMED);
        persistOrder(42L, OrderStatus.CONFIRMED);

        JsonNode page = getOrders("?status=CONFIRMED");

        assertThat(page.get("totalElements").asInt()).isEqualTo(2);
        page.get("content").forEach(node ->
                assertThat(node.get("status").asText()).isEqualTo("CONFIRMED"));
    }

    @Test
    void getAll_filtersByDateRange() throws Exception {
        stubAnyUser();
        Long oldOrderId = persistOrder(42L, OrderStatus.PENDING);
        persistOrder(42L, OrderStatus.PENDING);

        jdbcTemplate.update("UPDATE orders SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(10)), oldOrderId);

        String from = LocalDateTime.now().minusHours(1).toString();
        JsonNode page = getOrders("?createdFrom=" + from);

        assertThat(page.get("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    void getAll_returnsPaginated() throws Exception {
        stubAnyUser();
        for (int i = 0; i < 25; i++) {
            persistOrder(42L, OrderStatus.PENDING);
        }

        JsonNode firstPage = getOrders("?page=0&size=10");
        assertThat(firstPage.get("totalElements").asInt()).isEqualTo(25);
        assertThat(firstPage.get("content").size()).isEqualTo(10);

        JsonNode lastPage = getOrders("?page=2&size=10");
        assertThat(lastPage.get("content").size()).isEqualTo(5);
    }

    @Test
    void updateOrder_changesStatusInDatabase() {
        stubUserService(42L);
        Item item = createItem("Gizmo", new BigDecimal("15.00"));
        OrderDto created = restTemplate.postForEntity(
                "/api/v1/orders",
                new CreateOrderRequest(42L, List.of(new OrderItemRequest(item.getId(), 1))),
                OrderDto.class).getBody();
        assertThat(created).isNotNull();

        ResponseEntity<OrderWithUserDto> response = restTemplate.exchange(
                "/api/v1/orders/" + created.id(),
                HttpMethod.PUT,
                new HttpEntity<>(new UpdateOrderRequest(OrderStatus.CONFIRMED)),
                OrderWithUserDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.CONFIRMED);

        String dbStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, created.id());
        assertThat(dbStatus).isEqualTo("CONFIRMED");
    }

    @Test
    void deleteOrder_softDeletesRecord() {
        Item item = createItem("Trinket", new BigDecimal("5.00"));
        OrderDto created = restTemplate.postForEntity(
                "/api/v1/orders",
                new CreateOrderRequest(42L, List.of(new OrderItemRequest(item.getId(), 1))),
                OrderDto.class).getBody();
        assertThat(created).isNotNull();

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/api/v1/orders/" + created.id(), HttpMethod.DELETE, null, Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Void> getResponse = restTemplate.getForEntity(
                "/api/v1/orders/" + created.id(), Void.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(orderRepository.findById(created.id())).isEmpty();

        Boolean deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM orders WHERE id = ?", Boolean.class, created.id());
        assertThat(deleted).isTrue();
    }

    private Long persistOrder(Long userId, OrderStatus status) {
        Order order = new Order();
        order.setUserId(userId);
        order.setStatus(status);
        order.setTotalPrice(new BigDecimal("10.00"));
        order.setDeleted(false);
        return orderRepository.save(order).getId();
    }

    private void stubAnyUser() {
        wireMockServer.stubFor(get(urlMatching("/api/users/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"email\":\"u@test.com\",\"name\":\"N\",\"surname\":\"S\"}")));
    }

    private JsonNode getOrders(String query) throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/orders" + query, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody());
    }
}
