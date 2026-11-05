package com.kleinkauf.common.audit;

import com.kleinkauf.common.constants.Headers;
import com.kleinkauf.common.constants.TenantConstants;
import com.kleinkauf.common.servlet.tenant.TenantContext;
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
Who and from where: taken from the current request.
- Only what a person does is audited: a request with a user or a public one (self-service sign-up).
  Internal calls between services (e.g. sales reserving stock in products) and what runs without a request
  (scheduled tasks, startup) are not recorded: null.
- The role is the highest one the user has.
- The IP is set by the gateway (X-Client-Ip; the client cannot send it: ClientHeadersFilter removes it).
 */
public final class AuditRequest {

    // From highest to lowest
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
