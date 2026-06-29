package com.innowise.orderservice.contract;

import com.innowise.orderservice.TestcontainersConfiguration;
import com.innowise.orderservice.client.UserServiceClient;
import com.innowise.orderservice.model.dto.UserDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCC consumer-side contract test for orderservice → user-service interaction.
 *
 * <p>Stub Runner loads the userservice stub JAR from local Maven ({@code ~/.m2}) and starts
 * WireMock on port 8484 with the mappings generated from userservice's contracts. The test
 * verifies that {@link UserServiceClient} correctly handles those contract-derived responses,
 * including the circuit-breaker fallback path on 404.
 */
@SpringBootTest(
        properties = {"user-service.url=http://localhost:8484"}
)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@AutoConfigureStubRunner(
        ids = "com.innowise:userservice:+:stubs:8484",
        stubsMode = StubRunnerProperties.StubsMode.LOCAL
)
class UserServiceContractConsumerTest {

    @Autowired
    private UserServiceClient userServiceClient;

    @Test
    void getUserById_withExistingUser_matchesContractAndReturnsUserData() {
        UserDto user = userServiceClient.getUserById(1L);

        assertThat(user).isNotNull();
        assertThat(user.id()).isEqualTo(1L);
        assertThat(user.name()).isEqualTo("Contract");
        assertThat(user.surname()).isEqualTo("User");
        assertThat(user.email()).isEqualTo("contract@test.com");
    }

    @Test
    void getUserById_withUnknownUser_returnsFallbackNull() {
        UserDto user = userServiceClient.getUserById(99999L);

        assertThat(user).isNull();
    }
}
