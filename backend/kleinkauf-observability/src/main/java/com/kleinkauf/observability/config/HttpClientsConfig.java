package com.kleinkauf.observability.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class HttpClientsConfig {

    // Calls to users through eureka. With Spring's builder so they propagate the traceId and have metrics
    @Bean
    @LoadBalanced
    public RestTemplate usersRestTemplate(RestTemplateBuilder builder,
                                          @Value("${http.client.connect-timeout:2s}") Duration connectTimeout,
                                          @Value("${http.client.read-timeout:3s}") Duration readTimeout) {
        return builder
                .requestFactoryBuilder(ClientHttpRequestFactoryBuilder.simple())
                .connectTimeout(connectTimeout)
                .readTimeout(readTimeout)
                .build();
    }

    /*
    Collector readings: straight to each instance's IP and management port (no eureka).
    Without the builder on purpose: a trace every 10 s per instance would only fill Tempo with noise.
     */
    @Bean
    public RestTemplate pollingRestTemplate(@Value("${observability.poll.timeout:2s}") Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return new RestTemplate(factory);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
