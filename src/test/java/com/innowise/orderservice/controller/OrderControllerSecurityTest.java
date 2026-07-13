package com.innowise.orderservice.controller;

import com.innowise.orderservice.StubJwksUri;
import com.innowise.orderservice.config.JwtAuthenticationEntryPoint;
import com.innowise.orderservice.config.SecurityConfig;
import com.innowise.orderservice.model.OrderStatus;
import com.innowise.orderservice.model.dto.OrderWithUserDto;
import com.innowise.orderservice.model.dto.UserDto;
import com.innowise.orderservice.service.OrderCommandService;
import com.innowise.orderservice.service.OrderQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class})
@StubJwksUri
class OrderControllerSecurityTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, Month.JANUARY, 15, 12, 0, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderCommandService commandService;

    @MockitoBean
    private OrderQueryService queryService;

    @Test
    void getById_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/orders/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getById_returnsUnauthorizedWhenAuthorizationHeaderHoldsMalformedJwt() throws Exception {
        mockMvc.perform(get("/api/v1/orders/1")
                        .header("Authorization", "Bearer not-a-valid-jwt-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getById_returnsOkWhenAuthenticatedViaJwtWithoutHeaders() throws Exception {
        var user = new UserDto(1L, "user@example.com", "John", "Doe");
        var orderWithUserDto = new OrderWithUserDto(1L, OrderStatus.PENDING, BigDecimal.valueOf(199.99),
                List.of(), FIXED_NOW, FIXED_NOW, user);
        when(queryService.getById(1L, 1L, false)).thenReturn(orderWithUserDto);

        mockMvc.perform(get("/api/v1/orders/1").with(userJwt("1")))
                .andExpect(status().isOk());
    }

    private static RequestPostProcessor userJwt(String userId) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt.claim("sub", userId).claim("role", "USER"));
    }
}
