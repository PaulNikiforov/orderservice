package com.innowise.orderservice.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.innowise.orderservice.model.dto.UserDto;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that orderservice calls User Service at {@code GET /api/v1/users/{id}}.
 * Does not load Spring context — exercises RestClient + UserServiceClientImpl directly.
 */
class OrderToUserServiceClientTest {

    static WireMockServer wireMock;
    UserServiceClientImpl client;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        client = new UserServiceClientImpl(
                RestClient.builder().baseUrl("http://localhost:" + wireMock.port()).build()
        );
    }

    @Test
    void getUserById_sendsGetToV1UsersPath() {
        wireMock.stubFor(get(urlEqualTo("/api/v1/users/42"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":42,"email":"alice@example.com","name":"Alice","surname":"Smith"}
                                """)));

        UserDto result = client.getUserById(42L);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(42L);
        assertThat(result.name()).isEqualTo("Alice");
        assertThat(result.surname()).isEqualTo("Smith");
        wireMock.verify(1, getRequestedFor(urlEqualTo("/api/v1/users/42")));
    }

    @Test
    void getUserById_legacyPathWithoutV1IsNeverCalled() {
        try {
            client.getUserById(7L);
        } catch (Exception ignored) {}

        wireMock.verify(0, getRequestedFor(urlPathMatching("/api/users/.*")));
    }

    @Test
    void getUserById_pathContainsUserIdAsPathVariable() {
        wireMock.stubFor(get(urlPathMatching("/api/v1/users/\\d+"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":99,\"email\":\"b@b.com\",\"name\":\"Bob\",\"surname\":\"Jones\"}")));

        UserDto result = client.getUserById(99L);

        assertThat(result).isNotNull();
        wireMock.verify(1, getRequestedFor(urlEqualTo("/api/v1/users/99")));
    }
}
