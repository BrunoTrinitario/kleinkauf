package com.kleinkauf.users.util;

import com.kleinkauf.common.dto.TenantDto;
import com.kleinkauf.users.constants.CacheKeys;
import com.kleinkauf.users.dtos.*;
import com.kleinkauf.users.dtos.platform.TenantUsageDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
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
        // It used to use set(key, value, long): that is SETRANGE and the key never expired
        redisTemplate.opsForValue().set(
                cachedTenant.getKey(),
                "true",
                Duration.ofHours(cachedTenant.getDuration())
        );
    }

    // Read by the gateway on every request (by the Origin URL)
    public void saveTenantByUrl(String url, TenantDto tenant) {
        try {
            String key = CacheKeys.CACHE_TENANT_URL + url;
            redisTemplate.opsForHash().put(key, "data", objectMapper.writeValueAsString(tenant));
            redisTemplate.expire(key, Duration.ofHours(1));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    // When a role's permissions change: deletes the cached roles of all the store's users
    // (user:<id>:<tenant>, read by products). SCAN instead of KEYS so as not to block Redis
    public void deleteTenantUserRoles(String tenant) {
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions().match("user:*:" + tenant).count(500).build())) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // Per-tenant usage for the platform dashboard (5 minutes). If Redis fails it returns null and it is computed anyway
    public List<TenantUsageDto> getPlatformUsage() {
        try {
            Object json = redisTemplate.opsForValue().get(CacheKeys.PLATFORM_USAGE_USERS);
            if (json == null) return null;
            return objectMapper.readValue(json.toString(), new TypeReference<List<TenantUsageDto>>() {});
        } catch (Exception e) {
            log.warn("No se pudo leer el uso por tenant de Redis: {}", e.toString());
            return null;
        }
    }

    public void savePlatformUsage(List<TenantUsageDto> usage) {
        try {
            redisTemplate.opsForValue().set(CacheKeys.PLATFORM_USAGE_USERS, objectMapper.writeValueAsString(usage),
                    Duration.ofMinutes(5));
        } catch (Exception e) {
            log.warn("No se pudo guardar el uso por tenant en Redis: {}", e.toString());
        }
    }

    // Refresh tokens: each one issued is stored and expires with it (7 days)
    public void saveRefreshToken(String tenantSchema, Long userId, String tokenId, Duration ttl) {
        redisTemplate.opsForValue().set(CacheKeys.REFRESH_TOKEN(tenantSchema, userId, tokenId), "1", ttl);
    }

    public boolean isRefreshTokenActive(String tenantSchema, Long userId, String tokenId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(CacheKeys.REFRESH_TOKEN(tenantSchema, userId, tokenId)));
    }

    // When rotated: it keeps working for a while so two tabs renewing at the same time do not cut each other's session
    public void retireRefreshToken(String tenantSchema, Long userId, String tokenId, Duration grace) {
        redisTemplate.expire(CacheKeys.REFRESH_TOKEN(tenantSchema, userId, tokenId), grace);
    }

    public void deleteRefreshToken(String tenantSchema, Long userId, String tokenId) {
        redisTemplate.delete(CacheKeys.REFRESH_TOKEN(tenantSchema, userId, tokenId));
    }

    // When a superadmin is deactivated: their cached roles stop working (user:<id>:null, no tenant)
    public void deleteUserRoles(Long userId, String tenantSchema) {
        redisTemplate.delete(CacheKeys.CACHE_USER(userId, tenantSchema));
    }

    // When a tenant is disabled or deleted: so the gateway does not keep using stale data
    public void deleteTenant(String schema, String url) {
        redisTemplate.delete(List.of(
                CacheKeys.CACHE_TENANT + schema,
                CacheKeys.CACHE_TENANT_URL + url,
                CacheKeys.GATEWAY_CORS + url
        ));
    }

}
