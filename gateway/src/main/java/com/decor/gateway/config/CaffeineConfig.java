package com.decor.gateway.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CaffeineConfig {

    @Bean
    public Cache<String, Object> caffeineCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    @Bean
    public Cache<String, Integer> rateLimitedCache() {
        return Caffeine.newBuilder()
                .maximumSize(1_000_000)
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

}
