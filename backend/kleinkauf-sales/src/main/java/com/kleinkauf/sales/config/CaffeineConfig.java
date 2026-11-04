package com.kleinkauf.sales.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/*
The general cache (caffeineCache, 10 minutes) comes from kleinkauf-common.
Each user's roles carry their permissions: short TTL so a permission change
applies in less than 1 minute (users also deletes the ones in Redis).
 */
@Configuration
public class CaffeineConfig {

    @Bean
    public Cache<String, Object> userRolesCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .build();
    }
}
