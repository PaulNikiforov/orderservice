package com.innowise.orderservice.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.innowise.orderservice.StubJwksUri;
import com.innowise.orderservice.TestcontainersConfiguration;
import com.innowise.orderservice.model.Item;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderStatus;
import com.innowise.orderservice.model.dto.CreateOrderRequest;
import com.innowise.orderservice.model.dto.OrderDto;
import com.innowise.orderservice.model.dto.OrderItemRequest;
import com.innowise.orderservice.model.dto.OrderWithUserDto;
import com.innowise.orderservice.model.dto.UpdateOrderRequest;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@StubJwksUri
class OrderIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final String USER_EMAIL = "user@test.com";
    private static final Long OTHER_USER_ID = 2L;
    private static final String OTHER_USER_EMAIL = "alice@test.com";
    private static final Long ADMIN_ID = 99L;

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
    private MockMvc mockMvc;

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

    private static RequestPostProcessor userJwt(Long userId) {
        return jwt().jwt(j -> j.claim("sub", String.valueOf(userId)).claim("role", "USER"));
    }

    private static RequestPostProcessor adminJwt() {
        return jwt().jwt(j -> j.claim("sub", String.valueOf(ADMIN_ID)).claim("role", "ADMIN"));
    }

    private void stubUser(Long id, String email) {
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/api/v1/users/" + id))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":%d,"email":"%s","name":"John","surname":"Doe"}
                                """.formatted(id, email))));
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/api/v1/users/by-email"))
                .withQueryParam("email", equalTo(email))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":%d,"email":"%s","name":"John","surname":"Doe"}
                                """.formatted(id, email))));
    }

    private void stubUserByIdDown(Long id) {
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/api/v1/users/" + id))
                .willReturn(aResponse().withStatus(500)));
    }

    private void stubAnyUser() {
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlMatching("/api/v1/users/\\d+"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"email\":\"u@test.com\",\"name\":\"N\",\"surname\":\"S\"}")));
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlMatching("/api/v1/users/by-email.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"email\":\"u@test.com\",\"name\":\"N\",\"surname\":\"S\"}")));
    }

    private OrderDto createOrder(Long itemId, int quantity, RequestPostProcessor jwtPostProcessor) throws Exception {
        var request = new CreateOrderRequest(List.of(new OrderItemRequest(itemId, quantity)));
        String body = mockMvc.perform(post("/api/v1/orders")
                        .with(jwtPostProcessor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, OrderDto.class);
    }

    private JsonNode getOrders(String query, RequestPostProcessor jwtPostProcessor) throws Exception {
        String body = mockMvc.perform(get("/api/v1/orders" + query).with(jwtPostProcessor))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private Long persistOrder(String userEmail, OrderStatus status) {
        Order order = new Order();
        order.setUserEmail(userEmail);
        order.setStatus(status);
        order.setTotalPrice(new BigDecimal("10.00"));
        order.setDeleted(false);
        return orderRepository.save(order).getId();
    }

    @Test
    void createOrder_returnsCreatedDto() throws Exception {
        Item item = createItem("Widget", new BigDecimal("49.99"));
        stubUser(USER_ID, USER_EMAIL);
        var request = new CreateOrderRequest(List.of(new OrderItemRequest(item.getId(), 2)));

        String body = mockMvc.perform(post("/api/v1/orders")
                        .with(userJwt(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        OrderWithUserDto parsed = objectMapper.readValue(body, OrderWithUserDto.class);
        assertThat(parsed.user()).isNotNull();
        assertThat(parsed.user().email()).isEqualTo(USER_EMAIL);
        assertThat(parsed.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(parsed.totalPrice()).isEqualByComparingTo(new BigDecimal("99.98"));
    }

    @Test
    void createOrder_returns404_forUnknownItem() throws Exception {
        stubUser(USER_ID, USER_EMAIL);
        var request = new CreateOrderRequest(List.of(new OrderItemRequest(999999L, 1)));

        mockMvc.perform(post("/api/v1/orders")
                        .with(userJwt(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createOrder_returns401_withoutAuthentication() throws Exception {
        var request = new CreateOrderRequest(List.of(new OrderItemRequest(1L, 1)));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getById_persistsToDatabase_andIncludesUserInfo() throws Exception {
        Item item = createItem("Gadget", new BigDecimal("25.00"));
        stubUser(USER_ID, USER_EMAIL);
        OrderDto created = createOrder(item.getId(), 1, userJwt(USER_ID));

        String body = mockMvc.perform(get("/api/v1/orders/" + created.id()).with(userJwt(USER_ID)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        OrderWithUserDto fetched = objectMapper.readValue(body, OrderWithUserDto.class);
        assertThat(fetched.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(fetched.totalPrice()).isGreaterThan(BigDecimal.ZERO);
        assertThat(fetched.user()).isNotNull();
        assertThat(fetched.user().email()).isEqualTo(USER_EMAIL);
        assertThat(orderRepository.findById(created.id())).isPresent();
    }

    @Test
    void getById_returns404_whenOrderNotFound() throws Exception {
        stubUser(USER_ID, USER_EMAIL);

        mockMvc.perform(get("/api/v1/orders/999999").with(userJwt(USER_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getById_returns403_whenUserNotOwner() throws Exception {
        Item item = createItem("Gadget", new BigDecimal("25.00"));
        stubUser(USER_ID, USER_EMAIL);
        OrderDto created = createOrder(item.getId(), 1, userJwt(USER_ID));

        stubUser(OTHER_USER_ID, OTHER_USER_EMAIL);
        mockMvc.perform(get("/api/v1/orders/" + created.id()).with(userJwt(OTHER_USER_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getById_allowsAdmin_evenWhenNotOwner() throws Exception {
        Item item = createItem("Gadget", new BigDecimal("25.00"));
        stubUser(USER_ID, USER_EMAIL);
        OrderDto created = createOrder(item.getId(), 1, userJwt(USER_ID));

        mockMvc.perform(get("/api/v1/orders/" + created.id()).with(adminJwt()))
                .andExpect(status().isOk());
    }

    @Test
    void getById_returns503_whenUserServiceDownDuringOwnershipCheck() throws Exception {
        Item item = createItem("Gadget", new BigDecimal("25.00"));
        stubUser(USER_ID, USER_EMAIL);
        OrderDto created = createOrder(item.getId(), 1, userJwt(USER_ID));

        stubUserByIdDown(USER_ID);
        mockMvc.perform(get("/api/v1/orders/" + created.id()).with(userJwt(USER_ID)))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void getAll_filtersByStatus() throws Exception {
        stubAnyUser();
        persistOrder(USER_EMAIL, OrderStatus.PENDING);
        persistOrder(USER_EMAIL, OrderStatus.CONFIRMED);
        persistOrder(USER_EMAIL, OrderStatus.CONFIRMED);

        JsonNode page = getOrders("?statuses=CONFIRMED", adminJwt());

        assertThat(page.get("totalElements").asInt()).isEqualTo(2);
        page.get("content").forEach(node ->
                assertThat(node.get("status").asText()).isEqualTo("CONFIRMED"));
    }

    @Test
    void getAll_filtersByMultipleStatuses() throws Exception {
        stubAnyUser();
        persistOrder(USER_EMAIL, OrderStatus.PENDING);
        persistOrder(USER_EMAIL, OrderStatus.CONFIRMED);
        persistOrder(USER_EMAIL, OrderStatus.CANCELLED);

        JsonNode page = getOrders("?statuses=PENDING&statuses=CONFIRMED", adminJwt());

        assertThat(page.get("totalElements").asInt()).isEqualTo(2);
        page.get("content").forEach(node ->
                assertThat(node.get("status").asText()).isIn("PENDING", "CONFIRMED"));
    }

    @Test
    void getAll_admin_filtersByUserEmail() throws Exception {
        stubAnyUser();
        persistOrder(OTHER_USER_EMAIL, OrderStatus.PENDING);
        persistOrder(OTHER_USER_EMAIL, OrderStatus.CONFIRMED);
        persistOrder("bob@test.com", OrderStatus.PENDING);

        JsonNode page = getOrders("?userEmail=" + OTHER_USER_EMAIL, adminJwt());

        assertThat(page.get("totalElements").asInt()).isEqualTo(2);
        page.get("content").forEach(node -> {
            Long id = node.get("id").asLong();
            assertThat(orderRepository.findById(id))
                    .get()
                    .extracting(Order::getUserEmail)
                    .isEqualTo(OTHER_USER_EMAIL);
        });
    }

    @Test
    void getAll_user_ignoresClientEmailFilter_seesOnlyOwnOrders() throws Exception {
        stubUser(USER_ID, USER_EMAIL);
        persistOrder(USER_EMAIL, OrderStatus.PENDING);
        persistOrder(OTHER_USER_EMAIL, OrderStatus.PENDING);
        persistOrder(OTHER_USER_EMAIL, OrderStatus.CONFIRMED);

        JsonNode page = getOrders("?userEmail=" + OTHER_USER_EMAIL, userJwt(USER_ID));

        assertThat(page.get("totalElements").asInt()).isEqualTo(1);
        page.get("content").forEach(node -> {
            Long id = node.get("id").asLong();
            assertThat(orderRepository.findById(id))
                    .get()
                    .extracting(Order::getUserEmail)
                    .isEqualTo(USER_EMAIL);
        });
    }

    @Test
    void getAll_filtersByDateRange() throws Exception {
        stubAnyUser();
        LocalDateTime baseTime = LocalDateTime.of(2026, Month.JUNE, 1, 12, 0, 0);
        Long oldOrderId = persistOrder(USER_EMAIL, OrderStatus.PENDING);
        Long recentOrderId = persistOrder(USER_EMAIL, OrderStatus.PENDING);

        jdbcTemplate.update("UPDATE orders SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(baseTime.minusDays(10)), oldOrderId);
        jdbcTemplate.update("UPDATE orders SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(baseTime), recentOrderId);

        String from = baseTime.minusDays(1).toString();
        JsonNode page = getOrders("?createdFrom=" + from, adminJwt());

        assertThat(page.get("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    void getAll_returnsPaginated() throws Exception {
        stubAnyUser();
        for (int i = 0; i < 25; i++) {
            persistOrder(USER_EMAIL, OrderStatus.PENDING);
        }

        JsonNode firstPage = getOrders("?page=0&size=10", adminJwt());
        assertThat(firstPage.get("totalElements").asInt()).isEqualTo(25);
        assertThat(firstPage.get("content").size()).isEqualTo(10);

        JsonNode lastPage = getOrders("?page=2&size=10", adminJwt());
        assertThat(lastPage.get("content").size()).isEqualTo(5);
    }

    @Test
    void updateOrder_changesStatusInDatabase() throws Exception {
        stubUser(USER_ID, USER_EMAIL);
        Item item = createItem("Gizmo", new BigDecimal("15.00"));
        OrderDto created = createOrder(item.getId(), 1, userJwt(USER_ID));

        String body = mockMvc.perform(put("/api/v1/orders/" + created.id())
                        .with(userJwt(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateOrderRequest(OrderStatus.CONFIRMED))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        OrderWithUserDto updated = objectMapper.readValue(body, OrderWithUserDto.class);
        assertThat(updated.status()).isEqualTo(OrderStatus.CONFIRMED);

        String dbStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, created.id());
        assertThat(dbStatus).isEqualTo("CONFIRMED");
    }

    @Test
    void updateOrder_returns403_whenUserNotOwner() throws Exception {
        stubUser(USER_ID, USER_EMAIL);
        Item item = createItem("Gizmo", new BigDecimal("15.00"));
        OrderDto created = createOrder(item.getId(), 1, userJwt(USER_ID));

        stubUser(OTHER_USER_ID, OTHER_USER_EMAIL);
        mockMvc.perform(put("/api/v1/orders/" + created.id())
                        .with(userJwt(OTHER_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateOrderRequest(OrderStatus.CONFIRMED))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateOrder_allowsAdmin_evenWhenNotOwner() throws Exception {
        stubUser(USER_ID, USER_EMAIL);
        Item item = createItem("Gizmo", new BigDecimal("15.00"));
        OrderDto created = createOrder(item.getId(), 1, userJwt(USER_ID));

        mockMvc.perform(put("/api/v1/orders/" + created.id())
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateOrderRequest(OrderStatus.CONFIRMED))))
                .andExpect(status().isOk());
    }

    @Test
    void deleteOrder_softDeletesRecord() throws Exception {
        stubUser(USER_ID, USER_EMAIL);
        Item item = createItem("Trinket", new BigDecimal("5.00"));
        OrderDto created = createOrder(item.getId(), 1, userJwt(USER_ID));

        mockMvc.perform(delete("/api/v1/orders/" + created.id()).with(userJwt(USER_ID)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/orders/" + created.id()).with(userJwt(USER_ID)))
                .andExpect(status().isNotFound());
        assertThat(orderRepository.findById(created.id())).isEmpty();

        Boolean deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM orders WHERE id = ?", Boolean.class, created.id());
        assertThat(deleted).isTrue();
    }

    @Test
    void deleteOrder_returns403_whenUserNotOwner() throws Exception {
        stubUser(USER_ID, USER_EMAIL);
        Item item = createItem("Trinket", new BigDecimal("5.00"));
        OrderDto created = createOrder(item.getId(), 1, userJwt(USER_ID));

        stubUser(OTHER_USER_ID, OTHER_USER_EMAIL);
        mockMvc.perform(delete("/api/v1/orders/" + created.id()).with(userJwt(OTHER_USER_ID)))
                .andExpect(status().isForbidden());
    }
}
