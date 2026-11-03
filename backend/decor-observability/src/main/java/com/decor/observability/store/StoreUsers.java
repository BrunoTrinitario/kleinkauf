package com.decor.observability.store;

import com.decor.common.constants.ActivityKeys;
import com.decor.observability.audit.ActorNames;
import com.decor.observability.audit.AuditStore;
import com.decor.observability.dto.StoreUsersDto;
import com.decor.observability.dto.StoreUsersDto.ConnectedUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/*
Usuarios conectados de una tienda: las keys activity:<tenant>:<userId> que marca el gateway (vencen a los 5
minutos) con la hora de su ultima request. Se recorren con SCAN solo las de la tienda y los nombres salen de
ActorNames (users, con cache).
 */
@Slf4j
@Component
public class StoreUsers {

    private static final long SCAN_COUNT = 500;

    private final StringRedisTemplate redis;
    private final ActorNames names;

    public StoreUsers(StringRedisTemplate redis, ActorNames names) {
        this.redis = redis;
        this.names = names;
    }

    // null si no se pudo leer Redis
    public StoreUsersDto connected(String tenant) {
        // El tenant va en el patron del SCAN: solo con forma de schema (sin * ni ?)
        if (!AuditStore.validSchema(tenant)) {
            return new StoreUsersDto(0, List.of());
        }
        List<String> keys = new ArrayList<>();
        List<String> values;
        ScanOptions options = ScanOptions.scanOptions().match(ActivityKeys.PREFIX + tenant + ":*").count(SCAN_COUNT).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            cursor.forEachRemaining(keys::add);
            values = keys.isEmpty() ? List.of() : redis.opsForValue().multiGet(keys);
        } catch (RuntimeException e) {
            log.warn("No se pudieron leer los usuarios conectados de {}: {}", tenant, e.toString());
            return null;
        }

        List<ConnectedUser> users = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            Long userId = userId(keys.get(i));
            Instant lastActivity = epochMillis(values == null ? null : values.get(i));
            // Vencio entre el SCAN y la lectura, o no tiene la forma esperada
            if (userId == null || lastActivity == null) continue;
            users.add(new ConnectedUser(userId, names.name(userId, tenant), lastActivity));
        }
        users.sort(Comparator.comparing(ConnectedUser::lastActivity).reversed().thenComparing(ConnectedUser::userId));
        return new StoreUsersDto(users.size(), users);
    }

    private static Long userId(String key) {
        try {
            return Long.parseLong(key.substring(key.lastIndexOf(':') + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Instant epochMillis(String value) {
        if (value == null) return null;
        try {
            return Instant.ofEpochMilli(Long.parseLong(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
