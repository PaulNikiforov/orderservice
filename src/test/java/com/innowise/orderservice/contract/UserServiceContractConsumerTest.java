package com.innowise.orderservice.contract;

import com.innowise.orderservice.StubJwksUri;
import com.innowise.orderservice.TestcontainersConfiguration;
import com.innowise.orderservice.client.UserServiceClient;
import com.innowise.orderservice.model.dto.UserDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        properties = {"user-service.url=http://localhost:8484"}
)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@StubJwksUri
@AutoConfigureStubRunner(
        ids = "com.innowise:userservice:+:stubs:8484",
        stubsMode = StubRunnerProperties.StubsMode.LOCAL
)
class UserServiceContractConsumerTest {

    @Autowired
    private UserServiceClient userServiceClient;

    @BeforeEach
    void authenticateAsContractCaller() {
        Jwt jwt = Jwt.withTokenValue("contract-test-token")
                .header("alg", "none")
                .subject("100")
                .claim("role", "ADMIN")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getUserByEmail_withExistingUser_matchesContractAndReturnsUserData() {
        UserDto user = userServiceClient.getUserByEmail("contract@test.com");

        assertThat(user).isNotNull();
        assertThat(user.email()).isEqualTo("contract@test.com");
        assertThat(user.name()).isEqualTo("Contract");
        assertThat(user.surname()).isEqualTo("User");
    }

    @Test
    void getUserByEmail_withUnknownUser_returnsFallbackNull() {
        UserDto user = userServiceClient.getUserByEmail("unknown@test.com");

        assertThat(user).isNull();
    }

    @Test
    void getUserById_withExistingUser_matchesContractAndReturnsUserData() {
        UserDto user = userServiceClient.getUserById(100L);

        assertThat(user).isNotNull();
        assertThat(user.id()).isEqualTo(100L);
        assertThat(user.email()).isEqualTo("contract@test.com");
        assertThat(user.name()).isEqualTo("Contract");
        assertThat(user.surname()).isEqualTo("User");
    }

    @Test
    void getUserById_withUnknownUser_returnsFallbackNull() {
        UserDto user = userServiceClient.getUserById(999999L);

        assertThat(user).isNull();
    }
}
