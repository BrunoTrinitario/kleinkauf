package com.decor.product.util;

import com.decor.common.dto.RoleDto;
import com.decor.common.dto.TenantDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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
            if (rolesJson == null) {
                return  null;
            }
            return objectMapper.readValue(rolesJson, new TypeReference<List<RoleDto>>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public List<TenantDto> getAllTenants() {
        Set<String> keys = redisTemplate.keys("tenant:*");
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        List<TenantDto> tenants = new ArrayList<>();
        for (String key : keys) {
            String tenantJson =
                    (String) redisTemplate.opsForHash().get(key, "data");
            if (tenantJson == null) {
                continue;
            }
            try {
                TenantDto tenant = objectMapper.readValue(
                        tenantJson,
                        new TypeReference<TenantDto>() {}
                );
                tenants.add(tenant);
            } catch (JsonProcessingException ignored) {
            }
        }
        return tenants;
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
