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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/*
Online users: the activity:<tenant>:<userId> keys (logged in) and visitor:<tenant>:<visitorId> (anonymous) marked by
the gateway (expire after 5 minutes). Iterated with SCAN (does not block Redis like KEYS). With thousands of users a
per-store counter would be better.
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
        Map<String, Long> users = new HashMap<>();
        Map<String, Long> visitors = new HashMap<>();
        try {
            countByTenant(ActivityKeys.PREFIX, ActivityKeys::tenantOf, users);
            countByTenant(ActivityKeys.VISITOR_PREFIX, ActivityKeys::visitorTenantOf, visitors);
        } catch (RuntimeException e) {
            log.warn("No se pudieron contar los usuarios conectados: {}", e.toString());
            return null;
        }
        Set<String> all = new HashSet<>(users.keySet());
        all.addAll(visitors.keySet());
        List<TenantUsers> tenants = all.stream()
                .map(tenant -> new TenantUsers(tenant, users.getOrDefault(tenant, 0L), visitors.getOrDefault(tenant, 0L)))
                .sorted(Comparator.comparingLong((TenantUsers t) -> t.users() + t.visitors()).reversed()
                        .thenComparing(TenantUsers::tenant))
                .toList();
        return new ConnectedUsersDto(tenants.stream().mapToLong(TenantUsers::users).sum(),
                tenants.stream().mapToLong(TenantUsers::visitors).sum(), tenants);
    }

    private void countByTenant(String prefix, Function<String, String> tenantOf, Map<String, Long> counts) {
        ScanOptions options = ScanOptions.scanOptions().match(prefix + "*").count(SCAN_COUNT).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                String tenant = tenantOf.apply(cursor.next());
                if (tenant != null) {
                    counts.merge(tenant, 1L, Long::sum);
                }
            }
        }
    }
}
