package com.innowise.orderservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    private final String userServiceUrl;

    public RestClientConfig(@Value("${user-service.url}") String userServiceUrl) {
        this.userServiceUrl = userServiceUrl;
    }

    @Bean
    public RestClient restClient() {
        return RestClient.builder()
                .baseUrl(userServiceUrl)
                .build();
    }
}
