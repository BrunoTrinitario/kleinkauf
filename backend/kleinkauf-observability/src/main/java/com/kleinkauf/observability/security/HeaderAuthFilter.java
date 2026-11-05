package com.kleinkauf.observability.security;

import com.kleinkauf.common.constants.Headers;
import com.kleinkauf.common.constants.TenantConstants;
import com.kleinkauf.common.dto.RoleDto;
import com.kleinkauf.common.servlet.security.CustomUserDetails;
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
Builds the authenticated user from the X-User-Id header added by the gateway and their roles from users.
The tenant is the one in X-Forwarded-Host; without the header it is the platform (public).
The WS does not come through here with a user: it authenticates with the ticket.
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
