package com.decor.users.util;

import com.decor.common.constants.ActivityKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
La request mas reciente de cada tienda, de las keys activity:<tenant>:<userId> que marca el gateway (vencen a los
5 minutos, ver ActivityKeys). El uso por tenant la combina con el ultimo login: un usuario con la sesion abierta
sigue activo aunque no vuelva a loguearse.
Los valores son la hora en milisegundos (texto plano): se leen con StringRedisTemplate, no con el RedisTemplate de
JSON del servicio. Si Redis falla devuelve un mapa vacio y queda el ultimo login.
 */
@Slf4j
@Component
public class RecentActivity {

    private static final long SCAN_COUNT = 500;

    private final ObjectProvider<StringRedisTemplate> redis;

    public RecentActivity(ObjectProvider<StringRedisTemplate> redis) {
        this.redis = redis;
    }

    // schema -> ultima request, en la hora del servidor (como lastLogin)
    public Map<String, LocalDateTime> byTenant() {
        StringRedisTemplate template = redis.getIfAvailable();
        if (template == null) {
            return Map.of();
        }
        List<String> keys = new ArrayList<>();
        List<String> values;
        ScanOptions options = ScanOptions.scanOptions().match(ActivityKeys.PREFIX + "*").count(SCAN_COUNT).build();
        try (Cursor<String> cursor = template.scan(options)) {
            cursor.forEachRemaining(keys::add);
            values = keys.isEmpty() ? List.of() : template.opsForValue().multiGet(keys);
        } catch (RuntimeException e) {
            log.warn("No se pudo leer la ultima actividad de las tiendas: {}", e.toString());
            return Map.of();
        }
        Map<String, Long> latest = new HashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            String tenant = ActivityKeys.tenantOf(keys.get(i));
            Long millis = parse(values == null ? null : values.get(i));
            if (tenant != null && millis != null) {
                latest.merge(tenant, millis, Math::max);
            }
        }
        Map<String, LocalDateTime> result = new HashMap<>();
        latest.forEach((tenant, millis) ->
                result.put(tenant, LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())));
        return result;
    }

    private static Long parse(String value) {
        if (value == null) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
