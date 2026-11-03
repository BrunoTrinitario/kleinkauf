package com.kleinkauf.common.servlet.tenant;

import com.kleinkauf.common.constants.Headers;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/*
Toma el tenant del header que agrega el gateway y lo deja en el TenantContext.
Si tenantRequired es true y el header no viene, responde 404.
publicPaths: prefijos que no pertenecen a ningun tenant (ej. /products/images/): pasan sin el header.
 */
public class TenantFilter extends OncePerRequestFilter {

    private static final String TENANT_NOT_FOUND = "Tenant not found";

    private final boolean tenantRequired;
    private final List<String> publicPaths;

    public TenantFilter(boolean tenantRequired) {
        this(tenantRequired, List.of());
    }

    public TenantFilter(boolean tenantRequired, List<String> publicPaths) {
        this.tenantRequired = tenantRequired;
        // Sin vacios: un prefijo "" volveria publicas todas las rutas
        this.publicPaths = publicPaths == null ? List.of()
                : publicPaths.stream().map(String::trim).filter(p -> !p.isEmpty()).toList();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String tenant = request.getHeader(Headers.FORWARDED_HOST);

        if (tenantRequired && (tenant == null || tenant.isBlank()) && !isPublicPath(request.getRequestURI())) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("application/json");
            response.getWriter().write(TENANT_NOT_FOUND);
            return;
        }

        try {
            TenantContext.setCurrentTenant(tenant);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private boolean isPublicPath(String path) {
        return path != null && publicPaths.stream().anyMatch(path::startsWith);
    }
}
