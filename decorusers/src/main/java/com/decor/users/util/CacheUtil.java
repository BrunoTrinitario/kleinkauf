package com.decor.users.util;

import com.decor.users.dtos.ComTenantDto;
import com.decor.users.dtos.RoleDto;
import com.decor.users.dtos.TenantDto;
import com.decor.users.entities.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Component
public class CacheUtil {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    ObjectMapper objectMapper;

    public void saveOrUpdateUserCache(Long userId,String tenantSchema, List<RoleDto> roles) {
        String key = "user:" + userId + ":" + tenantSchema;
        String rolesJson = null;
        try {
            rolesJson = objectMapper.writeValueAsString(roles);
            redisTemplate.opsForHash().put(key, "roles", rolesJson);
            redisTemplate.expire(key, Duration.ofHours(1));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteUser(Long userId) {
        redisTemplate.opsForHash().delete(
                "user:" + userId,
                "roles");
    }

    public void saveOrUpdateTenantCache(String tenantSchema, TenantDto tenant) {
        String key = "tenant:" + tenantSchema;
        String dataJson = null;
        try {
            dataJson = objectMapper.writeValueAsString(tenant);
            redisTemplate.opsForHash().put(key, "data", dataJson);
            redisTemplate.expire(key, Duration.ofHours(1));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

}
