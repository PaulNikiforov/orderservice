package com.innowise.orderservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "user-service")
public record UserServiceProperties(String url, Duration connectTimeout, Duration readTimeout) {
}
