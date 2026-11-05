package com.kleinkauf.users.util;

import com.kleinkauf.common.constants.ActivityKeys;
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
The most recent request of each store, from the activity:<tenant>:<userId> keys marked by the gateway (expire after
5 minutes, see ActivityKeys). Per-tenant usage combines it with the last login: a user with an open session
is still active even if they do not log in again.
Values are the time in milliseconds (plain text): they are read with StringRedisTemplate, not with the service's
JSON RedisTemplate. If Redis fails it returns an empty map and the last login remains.
 */
@Slf4j
@Component
public class RecentActivity {

    private static final long SCAN_COUNT = 500;

    private final ObjectProvider<StringRedisTemplate> redis;

    public RecentActivity(ObjectProvider<StringRedisTemplate> redis) {
        this.redis = redis;
    }

    // schema -> last request, in server time (like lastLogin)
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
