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
Takes the tenant from the header added by the gateway and puts it in TenantContext.
If tenantRequired is true and the header is missing, responds 404.
publicPaths: prefixes that do not belong to any tenant (e.g. /products/images/): they pass without the header.
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
        // No empty ones: a "" prefix would make every route public
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
