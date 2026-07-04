package com.innowise.orderservice.client;

import com.innowise.orderservice.model.dto.UserDto;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.github.tomakehurst.wiremock.WireMockServer;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that orderservice calls User Service at {@code GET /api/v1/users/by-email?email={email}}.
 * Does not load Spring context — exercises RestClient + UserServiceClientImpl directly.
 */
class OrderToUserServiceClientTest {

    static WireMockServer wireMock;
    UserServiceClientImpl client;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
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
    void getUserByEmail_sendsGetToByEmailPath() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/users/by-email"))
                .withQueryParam("email", equalTo("alice@example.com"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":42,"email":"alice@example.com","name":"Alice","surname":"Smith"}
                                """)));

        UserDto result = client.getUserByEmail("alice@example.com");

        assertThat(result).isNotNull();
        assertThat(result.email()).isEqualTo("alice@example.com");
        assertThat(result.name()).isEqualTo("Alice");
        assertThat(result.surname()).isEqualTo("Smith");
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/api/v1/users/by-email"))
                .withQueryParam("email", equalTo("alice@example.com")));
    }

    @Test
    void getUserByEmail_pathByIdIsNeverCalled() {
        try {
            client.getUserByEmail("bob@example.com");
        } catch (Exception ignored) {}

        wireMock.verify(0, getRequestedFor(urlPathMatching("/api/v1/users/[0-9]+")));
    }

    @Test
    void getUserByEmail_emailPassedAsQueryParameter() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/users/by-email"))
                .withQueryParam("email", equalTo("carol@example.com"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":99,\"email\":\"carol@example.com\",\"name\":\"Carol\",\"surname\":\"Jones\"}")));

        UserDto result = client.getUserByEmail("carol@example.com");

        assertThat(result).isNotNull();
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/api/v1/users/by-email"))
                .withQueryParam("email", equalTo("carol@example.com")));
    }
}
