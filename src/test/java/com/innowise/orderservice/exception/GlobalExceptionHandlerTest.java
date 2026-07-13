package com.innowise.orderservice.exception;

import com.innowise.orderservice.StubJwksUri;
import com.innowise.orderservice.config.JwtAuthenticationEntryPoint;
import com.innowise.orderservice.config.SecurityConfig;
import com.innowise.orderservice.controller.OrderController;
import com.innowise.orderservice.service.OrderCommandService;
import com.innowise.orderservice.service.OrderQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class})
@StubJwksUri
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderQueryService queryService;

    @MockitoBean
    private OrderCommandService commandService;

    @Test
    void getById_returnsForbiddenWithErrorResponseWhenAccessDenied() throws Exception {
        when(queryService.getById(1L, 2L, false))
                .thenThrow(new OrderAccessDeniedException("Access denied to order 1"));

        mockMvc.perform(get("/api/v1/orders/1")
                        .with(jwt().jwt(j -> j.claim("sub", "2").claim("role", "USER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Access denied to order 1"))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/1"));
    }

    @Test
    void getById_returnsServiceUnavailableWithErrorResponseWhenUserServiceDown() throws Exception {
        when(queryService.getById(1L, 2L, false))
                .thenThrow(new UserServiceUnavailableException("User Service unavailable for id=2", new RuntimeException("timeout")));

        mockMvc.perform(get("/api/v1/orders/1")
                        .with(jwt().jwt(j -> j.claim("sub", "2").claim("role", "USER"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message").value("User Service unavailable for id=2"))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/1"));
    }
}
