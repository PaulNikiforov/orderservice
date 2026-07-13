package com.innowise.orderservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(UserServiceProperties.class)
public class RestClientConfig {

    @Bean
    public RestClient restClient(UserServiceProperties props) {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(props.connectTimeout())
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(props.readTimeout());
        return RestClient.builder()
                .baseUrl(props.url())
                .requestFactory(requestFactory)
                .build();
    }
}
