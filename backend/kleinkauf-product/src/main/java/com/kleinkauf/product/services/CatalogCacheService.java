package com.kleinkauf.product.services;

import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.function.Supplier;

/*
Public catalog cache in Redis, per tenant.
- Key: catalog:<tenant>:v<version>:<query>. Every change to a tenant's product bumps the version
  (catalog:<tenant>:version), so everything cached becomes stale instantly. 1-hour TTL as a backup.
- If Redis fails it reads from the database: the catalog does not depend on the cache.
 */
@Slf4j
@Service
public class CatalogCacheService {

    static final Duration TTL = Duration.ofHours(1);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    public <T> T get(String query, JavaType type, Supplier<T> loader) {
        String key;
        try {
            key = prefix() + ":v" + version() + ":" + query;
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.readValue((String) cached, type);
            }
        } catch (Exception e) {
            log.warn("Cache del catalogo no disponible: {}", e.toString());
            return loader.get();
        }

        T value = loader.get();
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), TTL);
        } catch (Exception e) {
            log.warn("No se pudo guardar el catalogo en cache: {}", e.toString());
        }
        return value;
    }

    /*
    Call after any change to a product of the current tenant.
    With an open transaction it applies after the commit: if it applied before, a concurrent
    read could cache the old data again with the new version.
     */
    public void invalidate() {
        String versionKey = prefix() + ":version";
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    increment(versionKey);
                }
            });
        } else {
            increment(versionKey);
        }
    }

    private void increment(String versionKey) {
        try {
            redisTemplate.opsForValue().increment(versionKey);
        } catch (Exception e) {
            log.warn("No se pudo invalidar el cache del catalogo: {}", e.toString());
        }
    }

    private long version() {
        Object version = redisTemplate.opsForValue().get(prefix() + ":version");
        return version == null ? 0 : Long.parseLong(version.toString());
    }

    private static String prefix() {
        return "catalog:" + TenantContext.getCurrentTenant();
    }
}
