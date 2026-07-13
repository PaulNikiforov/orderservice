package com.innowise.orderservice.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.innowise.orderservice.StubJwksUri;
import com.innowise.orderservice.TestcontainersConfiguration;
import com.innowise.orderservice.model.dto.UserDto;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@StubJwksUri
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

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetAll();
        circuitBreakerRegistry.circuitBreaker("userService").reset();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAs(String tokenValue) {
        Jwt jwt = Jwt.withTokenValue(tokenValue)
                .header("alg", "RS256")
                .claim("sub", "1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test
    void getUserByEmail_shouldReturnUserDto_whenUserExists() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/users/by-email"))
                .withQueryParam("email", equalTo("test@test.com"))
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
    void getUserByEmail_shouldReturnNull_whenServiceResponds404() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/users/by-email"))
                .withQueryParam("email", equalTo("unknown@test.com"))
                .willReturn(aResponse().withStatus(404)));

        assertThat(client.getUserByEmail("unknown@test.com")).isNull();
    }

    @Test
    void getUserByEmail_shouldNotOpenCircuit_whenServiceResponds404Repeatedly() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/users/by-email"))
                .withQueryParam("email", equalTo("unknown@test.com"))
                .willReturn(aResponse().withStatus(404)));

        for (int i = 0; i < 20; i++) {
            client.getUserByEmail("unknown@test.com");
        }

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("userService");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void getUserByEmail_shouldReturnNull_viaFallback_whenServiceResponds500() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/users/by-email"))
                .withQueryParam("email", equalTo("test@test.com"))
                .willReturn(aResponse().withStatus(500)));

        assertThat(client.getUserByEmail("test@test.com")).isNull();
    }

    @Test
    void getUserByEmail_shouldReturnNull_viaFallback_whenServiceUnavailable() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/users/by-email"))
                .withQueryParam("email", equalTo("other@test.com"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThat(client.getUserByEmail("other@test.com")).isNull();
    }

    @Test
    void getUserById_shouldReturnUserDto_whenUserExists() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/users/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":1,"email":"test@test.com","name":"John","surname":"Doe"}
                                """)));

        UserDto result = client.getUserById(1L);

        assertThat(result).isNotNull();
        assertThat(result.email()).isEqualTo("test@test.com");
        assertThat(result.name()).isEqualTo("John");
    }

    @Test
    void getUserById_shouldReturnNull_whenServiceResponds404() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/users/1"))
                .willReturn(aResponse().withStatus(404)));

        assertThat(client.getUserById(1L)).isNull();
    }

    @Test
    void getUserById_throwsUserServiceUnavailableException_whenServiceResponds500() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/users/1"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.getUserById(1L))
                .isInstanceOf(com.innowise.orderservice.exception.UserServiceUnavailableException.class);
    }

    @Test
    void getUserById_throwsUserServiceUnavailableException_whenServiceUnavailable() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/users/1"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> client.getUserById(1L))
                .isInstanceOf(com.innowise.orderservice.exception.UserServiceUnavailableException.class);
    }

    @Test
    void getUserById_sendsAuthorizationHeader_whenCallerAuthenticated() {
        authenticateAs("caller-token");
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/users/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":1,"email":"test@test.com","name":"John","surname":"Doe"}
                                """)));

        client.getUserById(1L);

        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/api/v1/users/1"))
                .withHeader("Authorization", equalTo("Bearer caller-token")));
    }

    @Test
    void getUserById_omitsAuthorizationHeader_whenNoAuthenticationPresent() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/users/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":1,"email":"test@test.com","name":"John","surname":"Doe"}
                                """)));

        client.getUserById(1L);

        wireMockServer.verify(0, getRequestedFor(urlPathEqualTo("/api/v1/users/1"))
                .withHeader("Authorization", matching(".*")));
    }

    @Test
    void getUserByEmail_omitsAuthorizationHeader_evenWhenCallerAuthenticated() {
        authenticateAs("caller-token");
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/users/by-email"))
                .withQueryParam("email", equalTo("test@test.com"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":1,"email":"test@test.com","name":"John","surname":"Doe"}
                                """)));

        client.getUserByEmail("test@test.com");

        wireMockServer.verify(0, getRequestedFor(urlPathEqualTo("/api/v1/users/by-email"))
                .withHeader("Authorization", matching(".*")));
    }
}
