package com.decor.observability.security;

import com.decor.common.constants.Headers;
import com.decor.common.constants.TenantConstants;
import com.decor.common.dto.RoleDto;
import com.decor.common.servlet.security.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/*
Arma el usuario autenticado con el header X-User-Id que agrega el gateway y sus roles de users.
El tenant es el de X-Forwarded-Host; sin el header es la plataforma (public).
El WS no pasa por aca con usuario: se autentica con el ticket.
 */
@Component
public class HeaderAuthFilter extends OncePerRequestFilter {

    private final RolesService rolesService;

    public HeaderAuthFilter(RolesService rolesService) {
        this.rolesService = rolesService;
    }

    public static String tenantOf(HttpServletRequest request) {
        String tenant = request.getHeader(Headers.FORWARDED_HOST);
        return tenant == null || tenant.isBlank() ? TenantConstants.PUBLIC : tenant;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String userIdHeader = request.getHeader(Headers.USER_ID);
        if (userIdHeader != null) {
            Long userId;
            try {
                userId = Long.parseLong(userIdHeader);
            } catch (NumberFormatException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            List<RoleDto> roles = rolesService.cached(userId, tenantOf(request));
            CustomUserDetails user = new CustomUserDetails(userId, roles == null ? List.of() : roles);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        }
        filterChain.doFilter(request, response);
    }
}
