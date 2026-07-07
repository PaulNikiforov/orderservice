package com.innowise.orderservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innowise.orderservice.StubJwksUri;
import com.innowise.orderservice.config.JwtAuthenticationEntryPoint;
import com.innowise.orderservice.config.SecurityConfig;
import com.innowise.orderservice.exception.ItemNotFoundException;
import com.innowise.orderservice.exception.OrderNotFoundException;
import com.innowise.orderservice.model.OrderStatus;
import com.innowise.orderservice.model.dto.CreateOrderRequest;
import com.innowise.orderservice.model.dto.OrderWithUserDto;
import com.innowise.orderservice.model.dto.UpdateOrderRequest;
import com.innowise.orderservice.model.dto.UserDto;
import com.innowise.orderservice.repository.specification.OrderFilterRequest;
import com.innowise.orderservice.service.OrderCommandService;
import com.innowise.orderservice.service.OrderQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class})
@StubJwksUri
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderCommandService commandService;

    @MockitoBean
    private OrderQueryService queryService;

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 1, 15, 12, 0, 0);

    private static RequestPostProcessor userJwt(String userId, String role) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt.claim("sub", userId).claim("role", role));
    }

    private OrderWithUserDto orderWithUserDto() {
        var user = new UserDto(1L, "user@example.com", "John", "Doe");
        return new OrderWithUserDto(1L, OrderStatus.PENDING, BigDecimal.valueOf(199.99),
                List.of(), FIXED_NOW, FIXED_NOW, user);
    }

    @Test
    void create_returns201_withValidRequest() throws Exception {
        when(commandService.create(any(CreateOrderRequest.class), any())).thenReturn(orderWithUserDto());

        mockMvc.perform(post("/api/v1/orders")
                        .with(userJwt("7", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"itemId":10,"quantity":2}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void create_returns400_whenBodyInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .with(userJwt("7", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void create_returns404_whenItemNotFound() throws Exception {
        when(commandService.create(any(CreateOrderRequest.class), any()))
                .thenThrow(new ItemNotFoundException("Item not found with id: 10"));

        mockMvc.perform(post("/api/v1/orders")
                        .with(userJwt("7", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"itemId":10,"quantity":2}]}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getById_returns200_withOrderData() throws Exception {
        when(queryService.getById(1L, 7L, false)).thenReturn(orderWithUserDto());

        mockMvc.perform(get("/api/v1/orders/1")
                        .with(userJwt("7", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.user.email").value("user@example.com"));
    }

    @Test
    void getById_returns404_whenNotFound() throws Exception {
        when(queryService.getById(99L, 7L, false)).thenThrow(new OrderNotFoundException("Order not found with id: 99"));

        mockMvc.perform(get("/api/v1/orders/99")
                        .with(userJwt("7", "USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order not found with id: 99"));
    }

    @Test
    void getAll_returns200_withDefaultPagination() throws Exception {
        var page = new PageImpl<>(List.of(orderWithUserDto()), PageRequest.of(0, 20), 1);
        when(queryService.getAll(any(OrderFilterRequest.class), any(), any(Boolean.class), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/orders")
                        .with(userJwt("7", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getAll_accepts_statusFilter() throws Exception {
        var page = new PageImpl<>(List.of(orderWithUserDto()), PageRequest.of(0, 20), 1);
        when(queryService.getAll(any(OrderFilterRequest.class), any(), any(Boolean.class), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/orders")
                        .with(userJwt("7", "USER"))
                        .param("statuses", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    void getAll_accepts_multipleStatusesFilter() throws Exception {
        var page = new PageImpl<>(List.of(orderWithUserDto()), PageRequest.of(0, 20), 1);
        when(queryService.getAll(any(OrderFilterRequest.class), any(), any(Boolean.class), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/orders")
                        .with(userJwt("7", "USER"))
                        .param("statuses", "PENDING")
                        .param("statuses", "CONFIRMED"))
                .andExpect(status().isOk());

        ArgumentCaptor<OrderFilterRequest> captor = ArgumentCaptor.forClass(OrderFilterRequest.class);
        verify(queryService).getAll(captor.capture(), any(), any(Boolean.class), any());
        assertThat(captor.getValue().statuses())
                .containsExactlyInAnyOrder(OrderStatus.PENDING, OrderStatus.CONFIRMED);
    }

    @Test
    void getAll_accepts_userEmailFilter() throws Exception {
        var page = new PageImpl<>(List.of(orderWithUserDto()), PageRequest.of(0, 20), 1);
        when(queryService.getAll(any(OrderFilterRequest.class), any(), any(Boolean.class), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/orders")
                        .with(userJwt("7", "USER"))
                        .param("userEmail", "alice@test.com"))
                .andExpect(status().isOk());

        ArgumentCaptor<OrderFilterRequest> captor = ArgumentCaptor.forClass(OrderFilterRequest.class);
        verify(queryService).getAll(captor.capture(), any(), any(Boolean.class), any());
        assertThat(captor.getValue().userEmail()).isEqualTo("alice@test.com");
    }

    @Test
    void getAll_accepts_dateRangeFilter() throws Exception {
        var page = new PageImpl<>(List.of(orderWithUserDto()), PageRequest.of(0, 20), 1);
        when(queryService.getAll(any(OrderFilterRequest.class), any(), any(Boolean.class), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/orders")
                        .with(userJwt("7", "USER"))
                        .param("createdFrom", "2026-01-01T00:00:00")
                        .param("createdTo", "2026-12-31T23:59:59"))
                .andExpect(status().isOk());
    }

    @Test
    void update_returns200_withUpdatedStatus() throws Exception {
        var updated = new OrderWithUserDto(1L, OrderStatus.CONFIRMED, BigDecimal.valueOf(199.99),
                List.of(), FIXED_NOW, FIXED_NOW, null);
        when(commandService.update(eq(1L), any(UpdateOrderRequest.class), eq(7L), eq(false))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/orders/1")
                        .with(userJwt("7", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CONFIRMED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void update_returns404_whenNotFound() throws Exception {
        when(commandService.update(eq(99L), any(UpdateOrderRequest.class), eq(7L), eq(false)))
                .thenThrow(new OrderNotFoundException("Order not found with id: 99"));

        mockMvc.perform(put("/api/v1/orders/99")
                        .with(userJwt("7", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CONFIRMED"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_returns400_whenBodyInvalid() throws Exception {
        mockMvc.perform(put("/api/v1/orders/1")
                        .with(userJwt("7", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":null}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_returns204_onSuccess() throws Exception {
        mockMvc.perform(delete("/api/v1/orders/1")
                        .with(userJwt("7", "USER")))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returns404_whenNotFound() throws Exception {
        doThrow(new OrderNotFoundException("Order not found with id: 99"))
                .when(commandService).delete(99L, 7L, false);

        mockMvc.perform(delete("/api/v1/orders/99")
                        .with(userJwt("7", "USER")))
                .andExpect(status().isNotFound());
    }
}
