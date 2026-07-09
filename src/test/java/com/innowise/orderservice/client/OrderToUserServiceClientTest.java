package com.innowise.orderservice.client;

import com.innowise.orderservice.model.dto.UserDto;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import com.github.tomakehurst.wiremock.WireMockServer;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

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
        SecurityContextHolder.clearContext();
        client = new UserServiceClientImpl(
                RestClient.builder().baseUrl("http://localhost:" + wireMock.port()).build()
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
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

    @Test
    void getUserById_sendsGetToIdPath() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/users/42"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":42,"email":"alice@example.com","name":"Alice","surname":"Smith"}
                                """)));

        UserDto result = client.getUserById(42L);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(42L);
        assertThat(result.email()).isEqualTo("alice@example.com");
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/api/v1/users/42")));
    }

    @Test
    void getUserById_sendsAuthorizationHeader_whenCallerAuthenticated() {
        Jwt jwt = Jwt.withTokenValue("caller-token")
                .header("alg", "RS256")
                .claim("sub", "42")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        wireMock.stubFor(get(urlPathEqualTo("/api/v1/users/42"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":42,"email":"alice@example.com","name":"Alice","surname":"Smith"}
                                """)));

        client.getUserById(42L);

        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/api/v1/users/42"))
                .withHeader("Authorization", equalTo("Bearer caller-token")));
    }
}
