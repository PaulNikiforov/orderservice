package com.innowise.orderservice.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.innowise.orderservice.TestcontainersConfiguration;
import com.innowise.orderservice.dto.UserDto;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class UserServiceClientTest {

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
    private UserServiceClient client;

    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetAll();
    }

    @Test
    void getUserById_shouldReturnUserDto_whenUserExists() {
        wireMockServer.stubFor(get(urlEqualTo("/api/users/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":1,"email":"test@test.com","name":"John","surname":"Doe"}
                                """)));

        UserDto result = client.getUserById(1L);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("John");
    }

    @Test
    void getUserById_shouldReturnNull_viaFallback_whenServiceResponds404() {
        wireMockServer.stubFor(get(urlEqualTo("/api/users/99"))
                .willReturn(aResponse().withStatus(404)));

        assertThat(client.getUserById(99L)).isNull();
    }

    @Test
    void getUserById_shouldReturnNull_viaFallback_whenServiceResponds500() {
        wireMockServer.stubFor(get(urlEqualTo("/api/users/1"))
                .willReturn(aResponse().withStatus(500)));

        assertThat(client.getUserById(1L)).isNull();
    }

    @Test
    void getUserById_shouldReturnNull_viaFallback_whenServiceUnavailable() {
        wireMockServer.stubFor(get(urlEqualTo("/api/users/2"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThat(client.getUserById(2L)).isNull();
    }
}
