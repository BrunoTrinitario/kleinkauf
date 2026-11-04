package com.kleinkauf.observability.collector;

import com.kleinkauf.common.constants.ActivityKeys;
import com.kleinkauf.observability.dto.ConnectedUsersDto;
import com.kleinkauf.observability.dto.ConnectedUsersDto.TenantUsers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
Online users: the activity:<tenant>:<userId> keys marked by the gateway (expire after 5 minutes).
Iterated with SCAN (does not block Redis like KEYS). With thousands of users a per-store counter would be better.
 */
@Slf4j
@Component
public class ConnectedUsers {

    private static final long SCAN_COUNT = 500;

    private final StringRedisTemplate redis;

    public ConnectedUsers(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // null if Redis could not be read
    public ConnectedUsersDto count() {
        Map<String, Long> byTenant = new HashMap<>();
        ScanOptions options = ScanOptions.scanOptions().match(ActivityKeys.PREFIX + "*").count(SCAN_COUNT).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                String tenant = ActivityKeys.tenantOf(cursor.next());
                if (tenant != null) {
                    byTenant.merge(tenant, 1L, Long::sum);
                }
            }
        } catch (RuntimeException e) {
            log.warn("No se pudieron contar los usuarios conectados: {}", e.toString());
            return null;
        }
        List<TenantUsers> tenants = byTenant.entrySet().stream()
                .map(entry -> new TenantUsers(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(TenantUsers::users).reversed().thenComparing(TenantUsers::tenant))
                .toList();
        return new ConnectedUsersDto(tenants.stream().mapToLong(TenantUsers::users).sum(), tenants);
    }
}
