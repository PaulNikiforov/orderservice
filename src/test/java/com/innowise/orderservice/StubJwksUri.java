package com.innowise.orderservice;

import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code AUTH_JWKS_URI} has no default in the base {@code application.yaml} (fail-fast in
 * docker/prod, matching {@code KAFKA_BOOTSTRAP_SERVERS}/{@code DB_URL}), so any test whose
 * context loads {@code OAuth2ResourceServerAutoConfiguration} needs this property supplied
 * directly — the JWKS endpoint URL is never actually called in tests (JWTs are injected via
 * {@code SecurityMockMvcRequestPostProcessors.jwt()} or fail structural parsing before any
 * network call), only resolved at context startup.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@TestPropertySource(properties = "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/oauth2/jwks")
public @interface StubJwksUri {
}
