package com.kleinkauf.observability.security;

import com.kleinkauf.common.dto.RoleDto;
import com.kleinkauf.observability.ws.WsTicket;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/*
Users' roles:
- cached: for REST, kept for 1 minute (as in products and sales: a removed role stops working within 1 minute).
- fresh: always from users. When issuing a ticket and when checking the open WSs (AccessRevalidator).
null: users did not answer.
 */
@Service
public class RolesService {

    public static final String SUPERADMIN = "SUPERADMIN";
    public static final String ADMINISTRADOR = "ADMINISTRADOR";

    private static final Duration CACHE_TTL = Duration.ofMinutes(1);

    private final UsersClient usersClient;
    private final Cache<String, List<RoleDto>> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(CACHE_TTL)
            .build();

    public RolesService(UsersClient usersClient) {
        this.usersClient = usersClient;
    }

    public List<RoleDto> cached(Long userId, String tenant) {
        String key = tenant + ":" + userId;
        List<RoleDto> roles = cache.getIfPresent(key);
        if (roles != null) {
            return roles;
        }
        roles = usersClient.getUserRoles(userId, tenant);
        if (roles != null) {
            cache.put(key, roles);
        }
        return roles;
    }

    public List<RoleDto> fresh(Long userId, String tenant) {
        List<RoleDto> roles = usersClient.getUserRoles(userId, tenant);
        if (roles != null) {
            cache.put(tenant + ":" + userId, roles);
        }
        return roles;
    }

    // The role needed for the WS: superadmin on the platform; in the store, the administrator
    public static String requiredRole(WsTicket ticket) {
        return ticket.isPlatform() ? SUPERADMIN : ADMINISTRADOR;
    }

    public static boolean has(List<RoleDto> roles, String role) {
        return roles != null && roles.stream().anyMatch(r -> role.equals(r.getType()));
    }
}
