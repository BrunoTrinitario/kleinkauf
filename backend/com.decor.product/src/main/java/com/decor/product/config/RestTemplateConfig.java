package com.decor.product.config;

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

    // Con timeouts para que una llamada a otro servicio caido no deje el hilo colgado.
    // Con el builder de Spring para que las llamadas propaguen el traceId y tengan metricas
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
