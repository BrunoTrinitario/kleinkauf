package com.decor.gateway.util;


import com.decor.gateway.constants.CacheKeys;
import com.decor.common.dto.TenantDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class CacheUtil {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ReactiveStringRedisTemplate reactiveRedisTemplate;

    @Autowired
    private ReactiveRedisTemplate<String, Object> reactiveJsonRedisTemplate;

    @Value("${limit.minutes}")
    private int minutesLimit;

    // Reactivo: se usa desde TenantGatewayFilter en cada request. Vacio si no esta o no se puede leer
    public Mono<TenantDto> getTenantBySchema(String schema) {
        String key = CacheKeys.TENANT_CACHE + schema;
        return reactiveJsonRedisTemplate.<String, Object>opsForHash().get(key, "data")
                .flatMap(tenantData -> {
                    try {
                        return Mono.just(objectMapper.readValue((String) tenantData, new TypeReference<TenantDto>() {}));
                    } catch (JsonProcessingException e) {
                        return Mono.empty();
                    }
                });
    }

    // Reactivo: se usa desde el filtro de CORS, que corre en el event loop de Netty
    public Mono<Boolean> existsTenantByUrl(String url) {
        String key = CacheKeys.GATEWAY_CORS + url;
        return reactiveRedisTemplate.hasKey(key)
                .map(Boolean.TRUE::equals);
    }

    public long requestCounter(String key){
        Long value = redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redisTemplate.expire(key, Duration.ofMinutes(minutesLimit));
        }
        return value != null ? value : 0L;
    }
}
