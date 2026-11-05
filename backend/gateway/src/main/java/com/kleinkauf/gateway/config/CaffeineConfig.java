package com.kleinkauf.gateway.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/*
The general cache (caffeineCache) comes from kleinkauf-common; these are the gateway's own
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

    // Tenants and CORS by URL. Short TTL: disabling or deleting a tenant takes up to 1 minute to apply
    @Bean
    public Cache<String, Object> tenantCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

}
