package com.kleinkauf.gateway.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    // Con el registro de observaciones: las llamadas a users propagan el traceId y tienen metricas
    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder(ObservationRegistry observationRegistry) {
        return WebClient.builder().observationRegistry(observationRegistry);
    }
}
