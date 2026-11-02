package com.decor.product.util;

import com.decor.common.dto.RoleDto;
import com.decor.common.dto.TenantDto;
import com.decor.product.dto.platform.ProductUsageDto;
import lombok.extern.slf4j.Slf4j;
import java.time.Duration;
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

@Slf4j
@Component
public class CacheUtil {

    // Uso por tenant del panel de la plataforma (users guarda el suyo en platform:usage:users)
    private static final String PLATFORM_USAGE = "platform:usage:products";

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

    // Uso por tenant del panel de la plataforma (5 minutos). Si Redis falla devuelve null y se calcula igual
    public List<ProductUsageDto> getPlatformUsage() {
        try {
            Object json = redisTemplate.opsForValue().get(PLATFORM_USAGE);
            if (json == null) return null;
            return objectMapper.readValue(json.toString(), new TypeReference<List<ProductUsageDto>>() {});
        } catch (Exception e) {
            log.warn("No se pudo leer el uso por tenant de Redis: {}", e.toString());
            return null;
        }
    }

    public void savePlatformUsage(List<ProductUsageDto> usage) {
        try {
            redisTemplate.opsForValue().set(PLATFORM_USAGE, objectMapper.writeValueAsString(usage), Duration.ofMinutes(5));
        } catch (Exception e) {
            log.warn("No se pudo guardar el uso por tenant en Redis: {}", e.toString());
        }
    }

    public TenantDto getTenantBySchema(String schema) {
        String key = "tenant:" + schema;
        String rolesJson = (String) redisTemplate.opsForHash().get(key, "data");
        try {
            if (rolesJson == null) {
                return null;
            }
            return objectMapper.readValue(rolesJson, new TypeReference<TenantDto>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }

}
