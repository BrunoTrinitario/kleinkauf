package com.kleinkauf.observability.store;

import com.kleinkauf.common.constants.ActivityKeys;
import com.kleinkauf.observability.audit.ActorNames;
import com.kleinkauf.observability.audit.AuditStore;
import com.kleinkauf.observability.dto.StoreUsersDto;
import com.kleinkauf.observability.dto.StoreUsersDto.ConnectedUser;
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
A store's online users: the activity:<tenant>:<userId> keys marked by the gateway (expire after 5
minutes) with the time of their last request. Only the store's keys are iterated with SCAN and the names come from
ActorNames (users, cached).
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

    // null if Redis could not be read
    public StoreUsersDto connected(String tenant) {
        // The tenant goes in the SCAN pattern: only if shaped like a schema (no * or ?)
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
            // Expired between the SCAN and the read, or does not have the expected shape
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
