package com.kleinkauf.users.security;

import com.kleinkauf.common.servlet.security.CustomUserDetails;

import com.kleinkauf.common.constants.Headers;
import com.kleinkauf.users.constants.PermittedUrls;
import com.kleinkauf.common.dto.RoleDto;
import com.kleinkauf.users.entities.Role;
import com.kleinkauf.users.repositories.RoleRepository;
import com.kleinkauf.users.services.RolePermissionService;
import com.kleinkauf.common.security.InternalTokenService;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Component
public class HeaderAuthFilter extends OncePerRequestFilter {

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    InternalTokenService internalTokenService;

    @Autowired
    RolePermissionService rolePermissionService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return PermittedUrls.getExcludedPaths().stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {


        String userIdHeader = request.getHeader(Headers.USER_ID);

        boolean isInternal = Boolean.parseBoolean(request.getHeader(Headers.INTERNAL_REQUEST));

        // /api/internal/** siempre pide el token interno, aunque no venga el header X-internal-request
        if (isInternal || request.getRequestURI().startsWith(PermittedUrls.INTERNAL_PATH)) {
            String token = request.getHeader(Headers.INTERNAL_TOKEN);
            if (internalTokenService.validateInternalToken(token)) {
                filterChain.doFilter(request, response);
            }else{
                rejectRequest(response, "Token invalido");
            }
            return;
        }

        boolean isOpenPath = Boolean.parseBoolean(request.getHeader(Headers.OPEN_PATH));

        if (isOpenPath){
            filterChain.doFilter(request, response);
            return;
        }

        if (userIdHeader != null) {

            Long userId = Long.parseLong(userIdHeader);

            Collection<Role> RawRoles = roleRepository.findByUser_Id(userId);

            List<RoleDto> roles = new ArrayList<>();
            for (Role r: RawRoles) {
                roles.add(
                        new RoleDto(
                                r.getType(),
                                rolePermissionService.maskFor(r.getType())
                        )
                );
            }

            CustomUserDetails userDetails = new CustomUserDetails(userId, roles);

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

            SecurityContextHolder.getContext().setAuthentication(auth);
            filterChain.doFilter(request, response);
        }else{
            rejectRequest(response, "usuario invalido");
        }
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