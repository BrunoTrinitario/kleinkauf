package com.decor.product.security;

import com.decor.common.servlet.security.CustomUserDetails;

import com.decor.common.constants.Headers;
import com.decor.product.constants.PermittedUrls;
import com.decor.common.dto.RoleDto;
import com.decor.product.services.UserService;
import com.decor.common.security.InternalTokenService;
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
    @Autowired
    InternalTokenService internalTokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String userIdHeader = request.getHeader(Headers.USER_ID);
        boolean isInternal = Boolean.parseBoolean(request.getHeader(Headers.INTERNAL_REQUEST));

        String path = request.getRequestURI();

        if (PermittedUrls.isPermitted(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isInternal) {
            String token = request.getHeader(Headers.INTERNAL_TOKEN);
            if (internalTokenService.validateInternalToken(token)) {
                filterChain.doFilter(request, response);
            }else{
                rejectRequest(response, "Token invalido");
            }
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

    private void rejectRequest(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        response.getWriter().write("""
        {
          "status": 403,
          "error": "Forbidden",
          "message": "%s"
        }
        """.formatted(message));

        response.getWriter().flush();
    }
}