package com.kleinkauf.users.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

// Calls to other services through Eureka (today only products, for the store name in emails), with timeouts
@Configuration
public class RestTemplateConfig {

    @Value("${http.client.connect-timeout:2s}")
    private Duration connectTimeout;

    @Value("${http.client.read-timeout:3s}")
    private Duration readTimeout;

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .requestFactoryBuilder(ClientHttpRequestFactoryBuilder.simple())
                .connectTimeout(connectTimeout)
                .readTimeout(readTimeout)
                .build();
    }
}
