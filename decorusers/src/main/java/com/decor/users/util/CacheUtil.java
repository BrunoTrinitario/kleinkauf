package com.decor.users.util;

import com.decor.users.dtos.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CacheUtil {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    ObjectMapper objectMapper;

    public void saveOrUpdateUserCache(CachedUser cachedUser) {
        String rolesJson = null;
        try {
            rolesJson = objectMapper.writeValueAsString(cachedUser.getRoles());
            redisTemplate.opsForHash().put(cachedUser.getKey(), cachedUser.getHashKey(), rolesJson);
            redisTemplate.expire(cachedUser.getKey(), Duration.ofHours(cachedUser.getDuration()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteUser(CachedUser cachedUser) {
        redisTemplate.opsForHash().delete(
                cachedUser.getKey(),
                cachedUser.getHashKey());
    }

    public void saveOrUpdateTenantCache(CachedTenant cachedTenant) {
        String dataJson = null;
        try {
            dataJson = objectMapper.writeValueAsString(cachedTenant.getTenantData());
            redisTemplate.opsForHash().put(cachedTenant.getKey(), cachedTenant.getHashKey(), dataJson);
            redisTemplate.expire(cachedTenant.getKey(), Duration.ofHours(1));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
    public void saveOrUpdateTenantCors(CachedTenant cachedTenant){
        redisTemplate.opsForValue().set(
                cachedTenant.getKey(),
                "true",
                cachedTenant.getDuration()
        );
    }

}
