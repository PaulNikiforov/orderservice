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
    void getUserById_shouldReturnUserDto_whenUserExists() {
        wireMockServer.stubFor(
                get(urlEqualTo("/api/users/1"))
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
    void getUserById_shouldReturnNull_whenServiceResponds404() {
        wireMockServer.stubFor(
                get(urlEqualTo("/api/users/99"))
                        .willReturn(aResponse().withStatus(404)));

        UserDto result = client.getUserById(99L);

        assertThat(result).isNull();
    }

    @Test
    void getUserById_shouldReturnNull_whenServiceResponds500() {
        wireMockServer.stubFor(
                get(urlEqualTo("/api/users/1"))
                        .willReturn(aResponse().withStatus(500)));

        UserDto result = client.getUserById(1L);

        assertThat(result).isNull();
    }

    @Test
    void getUserById_shouldReturnNull_whenServiceUnavailable() {
        RestClient unreachable = RestClient.builder()
                .baseUrl("http://localhost:19999")
                .build();
        UserDto result = new UserServiceClientImpl(unreachable).getUserById(1L);

        assertThat(result).isNull();
    }
}
