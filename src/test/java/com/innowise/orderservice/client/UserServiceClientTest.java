// FILE: src/test/java/com/innowise/orderservice/client/UserServiceClientTest.java
package com.innowise.orderservice.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.innowise.orderservice.dto.UserDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

class UserServiceClientTest {

    private WireMockServer wireMockServer;
    private UserServiceClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();

        wireMockServer.stubFor(
                get(urlPathEqualTo("/api/users/by-email"))
                        .withQueryParam("email", equalTo("test@test.com"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {"id":1,"email":"test@test.com","name":"John","surname":"Doe"}
                                        """))
        );

        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMockServer.port())
                .build();

        client = new UserServiceClientImpl(restClient);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void getUserByEmail_shouldReturnUserDto_whenUserExists() {
        UserDto result = client.getUserByEmail("test@test.com");

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("John");
        assertThat(result.surname()).isEqualTo("Doe");
    }

    @Test
    void getUserByEmail_shouldReturnNull_whenServiceResponds500() {
        wireMockServer.stubFor(
                get(urlPathEqualTo("/api/users/by-email"))
                        .withQueryParam("email", equalTo("test@test.com"))
                        .willReturn(aResponse()
                                .withStatus(500))
        );

        UserDto result = client.getUserByEmail("test@test.com");

        assertThat(result).isNull();
    }

    @Test
    void getUserById_shouldReturnUserDto_whenUserExists() {
        wireMockServer.stubFor(
                get(urlEqualTo("/api/users/1"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {"id":1,"email":"test@test.com","name":"John","surname":"Doe"}
                                        """))
        );

        UserDto result = client.getUserById(1L);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("John");
    }

    @Test
    void getUserById_shouldReturnNull_whenServiceResponds404() {
        wireMockServer.stubFor(
                get(urlEqualTo("/api/users/99"))
                        .willReturn(aResponse()
                                .withStatus(404))
        );

        UserDto result = client.getUserById(99L);

        assertThat(result).isNull();
    }

    @Test
    void getUserByEmail_shouldReturnNull_whenServiceUnavailable() {
        RestClient unreachableRestClient = RestClient.builder()
                .baseUrl("http://localhost:19999")
                .build();
        UserServiceClient unreachableClient = new UserServiceClientImpl(unreachableRestClient);

        UserDto result = unreachableClient.getUserByEmail("test@test.com");

        assertThat(result).isNull();
    }

    @Test
    void getUserByEmail_shouldReturnNull_fallbackIsStableUnderRepeatedFailures() {
        wireMockServer.stubFor(
                get(urlPathEqualTo("/api/users/by-email"))
                        .withQueryParam("email", equalTo("test@test.com"))
                        .willReturn(aResponse()
                                .withStatus(500))
        );

        UserDto result = null;
        for (int i = 0; i < 5; i++) {
            result = client.getUserByEmail("test@test.com");
        }

        assertThat(result).isNull();
    }
}
