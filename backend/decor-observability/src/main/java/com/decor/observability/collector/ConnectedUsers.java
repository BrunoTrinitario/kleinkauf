package com.decor.observability.collector;

import com.decor.common.constants.ActivityKeys;
import com.decor.observability.dto.ConnectedUsersDto;
import com.decor.observability.dto.ConnectedUsersDto.TenantUsers;
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
Usuarios conectados: las keys activity:<tenant>:<userId> que marca el gateway (vencen a los 5 minutos).
Se recorren con SCAN (no bloquea Redis como KEYS). Con miles de usuarios conviene un contador por tienda.
 */
@Slf4j
@Component
public class ConnectedUsers {

    private static final long SCAN_COUNT = 500;

    private final StringRedisTemplate redis;

    public ConnectedUsers(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // null si no se pudo leer Redis
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
