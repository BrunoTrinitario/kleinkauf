package com.decor.common.audit;

import com.decor.common.constants.Headers;
import com.decor.common.constants.TenantConstants;
import com.decor.common.servlet.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Set;

/*
Quien y desde donde: lo toma de la request en curso.
- Se audita solo lo que hace una persona: una request con usuario o una publica (el alta por autoservicio).
  Las llamadas internas entre servicios (ej. sales reservando stock en products) y lo que corre sin request
  (tareas programadas, el arranque) no se registran: null.
- El rol es el mas alto que tiene el usuario.
- La IP la pone el gateway (X-Client-Ip, el cliente no puede mandarla: ClientHeadersFilter la borra).
 */
public final class AuditRequest {

    // De mas alto a mas bajo
    static final List<String> ROLE_PRIORITY = List.of("SUPERADMIN", "ADMINISTRADOR", "EMPLEADO", "CLIENTE");

    public record Info(String tenant, AuditEvent.Actor actor, String ip, String traceId) {
    }

    private AuditRequest() {
    }

    public static Info current() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        if (Boolean.parseBoolean(request.getHeader(Headers.INTERNAL_REQUEST))) {
            return null;
        }
        String tenant = TenantContext.getCurrentTenant();
        String ip = request.getHeader(Headers.CLIENT_IP);
        return new Info(
                tenant == null || tenant.isBlank() ? TenantConstants.PUBLIC : tenant,
                actor(request),
                ip == null || ip.isBlank() ? request.getRemoteAddr() : ip,
                MDC.get("traceId"));
    }

    private static AuditEvent.Actor actor(HttpServletRequest request) {
        Long userId = parse(request.getHeader(Headers.USER_ID));
        if (userId == null) {
            return null;
        }
        return new AuditEvent.Actor(userId, null, highestRole());
    }

    private static String highestRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        Set<String> roles = new java.util.HashSet<>();
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String name = authority.getAuthority();
            if (name != null && name.startsWith("ROLE_")) {
                roles.add(name.substring("ROLE_".length()));
            }
        }
        return ROLE_PRIORITY.stream().filter(roles::contains).findFirst().orElse(null);
    }

    private static Long parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
