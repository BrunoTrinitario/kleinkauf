package com.decor.sales.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/*
El cache general (caffeineCache, 10 minutos) viene de decor-common.
Los roles de cada usuario traen sus permisos: TTL corto para que un cambio de permisos
se aplique en menos de 1 minuto (users ademas borra los de Redis).
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
