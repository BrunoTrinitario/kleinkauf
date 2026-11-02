package com.decor.users.util;

import com.decor.common.dto.TenantDto;
import com.decor.users.constants.CacheKeys;
import com.decor.users.dtos.*;
import com.decor.users.dtos.platform.TenantUsageDto;
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
        // Antes usaba set(key, value, long): eso es SETRANGE y la clave nunca vencia
        redisTemplate.opsForValue().set(
                cachedTenant.getKey(),
                "true",
                Duration.ofHours(cachedTenant.getDuration())
        );
    }

    // Lo lee el gateway en cada request (por la URL del Origin)
    public void saveTenantByUrl(String url, TenantDto tenant) {
        try {
            String key = CacheKeys.CACHE_TENANT_URL + url;
            redisTemplate.opsForHash().put(key, "data", objectMapper.writeValueAsString(tenant));
            redisTemplate.expire(key, Duration.ofHours(1));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    // Al cambiar los permisos de un rol: borra los roles cacheados de todos los usuarios de la tienda
    // (user:<id>:<tenant>, los lee products). SCAN en vez de KEYS para no bloquear Redis
    public void deleteTenantUserRoles(String tenant) {
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions().match("user:*:" + tenant).count(500).build())) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // Uso por tenant del panel de la plataforma (5 minutos). Si Redis falla devuelve null y se calcula igual
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

    // Al desactivar un superadmin: sus roles cacheados dejan de valer (user:<id>:null, sin tenant)
    public void deleteUserRoles(Long userId, String tenantSchema) {
        redisTemplate.delete(CacheKeys.CACHE_USER(userId, tenantSchema));
    }

    // Al deshabilitar o borrar un tenant: que el gateway no siga usando datos viejos
    public void deleteTenant(String schema, String url) {
        redisTemplate.delete(List.of(
                CacheKeys.CACHE_TENANT + schema,
                CacheKeys.CACHE_TENANT_URL + url,
                CacheKeys.GATEWAY_CORS + url
        ));
    }

}
