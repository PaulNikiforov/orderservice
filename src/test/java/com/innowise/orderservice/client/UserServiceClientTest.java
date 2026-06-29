package com.innowise.orderservice.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.innowise.orderservice.TestcontainersConfiguration;
import com.innowise.orderservice.model.dto.UserDto;
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
    void getUserByEmail_shouldReturnUserDto_whenUserExists() {
        wireMockServer.stubFor(get(urlEqualTo("/api/users/by-email?email=test@test.com"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":1,"email":"test@test.com","name":"John","surname":"Doe"}
                                """)));

        UserDto result = client.getUserByEmail("test@test.com");

        assertThat(result).isNotNull();
        assertThat(result.email()).isEqualTo("test@test.com");
        assertThat(result.name()).isEqualTo("John");
    }

    @Test
    void getUserByEmail_shouldReturnNull_viaFallback_whenServiceResponds404() {
        wireMockServer.stubFor(get(urlEqualTo("/api/users/by-email?email=unknown@test.com"))
                .willReturn(aResponse().withStatus(404)));

        assertThat(client.getUserByEmail("unknown@test.com")).isNull();
    }

    @Test
    void getUserByEmail_shouldReturnNull_viaFallback_whenServiceResponds500() {
        wireMockServer.stubFor(get(urlEqualTo("/api/users/by-email?email=test@test.com"))
                .willReturn(aResponse().withStatus(500)));

        assertThat(client.getUserByEmail("test@test.com")).isNull();
    }

    @Test
    void getUserByEmail_shouldReturnNull_viaFallback_whenServiceUnavailable() {
        wireMockServer.stubFor(get(urlEqualTo("/api/users/by-email?email=other@test.com"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThat(client.getUserByEmail("other@test.com")).isNull();
    }
}
