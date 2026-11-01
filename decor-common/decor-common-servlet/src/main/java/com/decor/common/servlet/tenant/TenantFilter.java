package com.decor.common.servlet.tenant;

import com.decor.common.constants.Headers;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/*
Toma el tenant del header que agrega el gateway y lo deja en el TenantContext.
Si tenantRequired es true y el header no viene, responde 404.
 */
public class TenantFilter extends OncePerRequestFilter {

    private static final String TENANT_NOT_FOUND = "Tenant not found";

    private final boolean tenantRequired;

    public TenantFilter(boolean tenantRequired) {
        this.tenantRequired = tenantRequired;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String tenant = request.getHeader(Headers.FORWARDED_HOST);

        if (tenantRequired && (tenant == null || tenant.isBlank())) {
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
}
