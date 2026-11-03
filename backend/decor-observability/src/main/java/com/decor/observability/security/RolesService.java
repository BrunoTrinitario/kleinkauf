package com.decor.observability.security;

import com.decor.common.dto.RoleDto;
import com.decor.observability.ws.WsTicket;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/*
Roles de los usuarios:
- cached: para el REST, guardados 1 minuto (como en products y sales: un rol quitado deja de valer en hasta 1 minuto).
- fresh: siempre a users. Al emitir un ticket y al revisar los WS abiertos (AccessRevalidator).
null: users no respondio.
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

    // El rol que hace falta para el WS: superadmin en la plataforma; en la tienda, el administrador (etapa 3)
    public static String requiredRole(WsTicket ticket) {
        return ticket.isPlatform() ? SUPERADMIN : ADMINISTRADOR;
    }

    public static boolean has(List<RoleDto> roles, String role) {
        return roles != null && roles.stream().anyMatch(r -> role.equals(r.getType()));
    }
}
