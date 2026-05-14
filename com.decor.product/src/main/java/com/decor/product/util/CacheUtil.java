package com.decor.product.util;

import com.decor.product.dto.RoleDto;
import com.decor.product.dto.TenantDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class CacheUtil {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    ObjectMapper objectMapper;

    public List<RoleDto> getUserRoles(Long userId, String tenantSchema) {
        String key = "user:" + userId + ":" + tenantSchema;
        String rolesJson = (String) redisTemplate.opsForHash().get(key, "roles");
        try {
            return objectMapper.readValue(rolesJson, new TypeReference<List<RoleDto>>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public TenantDto getTenantBySchema(String schema) {
        String key = "tenant:" + schema;
        String rolesJson = (String) redisTemplate.opsForHash().get(key, "data");
        try {
            return objectMapper.readValue(rolesJson, new TypeReference<TenantDto>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }

}
