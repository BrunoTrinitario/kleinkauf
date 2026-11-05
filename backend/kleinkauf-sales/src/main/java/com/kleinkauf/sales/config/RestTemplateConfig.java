package com.kleinkauf.sales.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Value("${http.client.connect-timeout:2s}")
    private Duration connectTimeout;

    @Value("${http.client.read-timeout:3s}")
    private Duration readTimeout;

    // With timeouts so a call to another service that is down does not leave the thread hanging.
    // With Spring's builder so calls propagate the traceId and have metrics
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
