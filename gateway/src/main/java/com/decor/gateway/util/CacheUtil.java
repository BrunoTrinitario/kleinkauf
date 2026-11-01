package com.decor.gateway.util;


import com.decor.gateway.constants.CacheKeys;
import com.decor.common.dto.TenantDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${limit.minutes}")
    private int minutesLimit;

    public TenantDto getTenantBySchema(String schema) {
        String key = CacheKeys.TENANT_CACHE + schema;
        String tenantDataString = (String) redisTemplate.opsForHash().get(key, "data");
        try {
            if (tenantDataString == null) {
                return null;
            }
            return objectMapper.readValue(tenantDataString, new TypeReference<TenantDto>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
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
