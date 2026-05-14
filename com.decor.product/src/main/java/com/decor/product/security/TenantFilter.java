package com.decor.product.security;

import com.decor.product.constants.ErrorMessages;
import com.decor.product.constants.Headers;
import com.decor.product.interceptor.TenantContext;
import com.decor.product.util.ResponseUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String tenant = request.getHeader(Headers.FORWARDED_HOST);

        if (tenant == null || tenant.isBlank()) {
            ResponseUtil.tenantNotFoundResponse(response);
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
