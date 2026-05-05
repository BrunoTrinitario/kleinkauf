package com.decor.product.security;

import com.decor.product.CacheUtil;
import com.decor.product.constants.PermittedUrls;
import com.decor.product.dto.RoleDto;
import com.decor.product.services.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class HeaderAuthFilter extends OncePerRequestFilter {

    @Autowired
    UserService userService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String userIdHeader = request.getHeader("X-User-Id");

        boolean isInternal = Boolean.parseBoolean(request.getHeader("X-internal-request"));

        String path = request.getRequestURI();

        if (PermittedUrls.isPermitted(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isInternal) {
            filterChain.doFilter(request, response);
            return;
        }

        if (userIdHeader != null) {

            Long userId = Long.parseLong(userIdHeader);

            List<RoleDto> roles = userService.getUserRolesById(userId);

            CustomUserDetails userDetails =
                    new CustomUserDetails(userId, roles);

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}