package com.kleinkauf.gateway.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/*
El cache general (caffeineCache) viene de kleinkauf-common; aca los propios del gateway
 */
@Configuration
public class CaffeineConfig {

    @Bean
    public Cache<String, Integer> rateLimitedCache() {
        return Caffeine.newBuilder()
                .maximumSize(1_000_000)
                .expireAfterWrite(2, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    // Tenants y CORS por URL. TTL corto: deshabilitar o borrar un tenant tarda hasta 1 minuto en aplicarse
    @Bean
    public Cache<String, Object> tenantCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

}
