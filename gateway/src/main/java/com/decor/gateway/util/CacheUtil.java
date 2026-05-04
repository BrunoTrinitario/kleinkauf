package com.decor.gateway.util;

import com.decor.gateway.dtos.ComTenantDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class CacheUtil {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public ComTenantDto getFullTenant(String tenantUrl) {
        String key = "tenant:" + tenantUrl;

        Object cached = redisTemplate.opsForHash().get(key, "data");

        if (cached == null) {
            return null;
        }

        return (ComTenantDto) cached;
    }
}
