package com.decor.product.services;

import com.decor.common.servlet.tenant.TenantContext;
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
Cache del catalogo publico en Redis, por tenant.
- Clave: catalog:<tenant>:v<version>:<consulta>. Cada cambio en un producto del tenant sube la version
  (catalog:<tenant>:version), asi todo lo cacheado queda viejo al instante. TTL de 1 hora como respaldo.
- Si Redis falla se lee de la base: el catalogo no depende del cache.
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
    Llamar despues de cualquier cambio en un producto del tenant actual.
    Con una transaccion abierta se aplica despues del commit: si se aplicara antes, una lectura
    concurrente podria volver a cachear los datos viejos con la version nueva.
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
