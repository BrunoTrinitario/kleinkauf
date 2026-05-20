package com.decor.gateway.util;


import com.decor.gateway.dtos.TenantDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CacheUtil {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public TenantDto getTenantBySchema(String schema) {
        String key = "tenant:" + schema;
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

    public boolean existsTenantByUrl(String url) {
        String key = "gateway:cors:" + url;
        String tenantDataString = (String) redisTemplate.opsForHash().get(key, "true");
        if (tenantDataString == null) {
            return false;
        }
        return true;
    }

    public long requestCounter(String key){
        Long value = redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }
        return value != null ? value : 0L;
    }
}
