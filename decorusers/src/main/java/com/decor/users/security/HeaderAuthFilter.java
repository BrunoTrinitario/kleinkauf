package com.decor.users.security;

import com.decor.users.constants.PermittedUrls;
import com.decor.users.dtos.RoleDto;
import com.decor.users.entities.Role;
import com.decor.users.repositories.RoleRepository;
import com.decor.users.util.JwtUtil;
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

import static org.aspectj.weaver.tools.cache.SimpleCacheFactory.path;

@Component
public class HeaderAuthFilter extends OncePerRequestFilter {

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    JwtUtil jwtUtil;

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


        String userIdHeader = request.getHeader("X-User-Id");

        boolean isInternal = Boolean.parseBoolean(request.getHeader("X-internal-request"));

        if (isInternal) {
            String token = request.getHeader("X-Internal-Token");
            if (jwtUtil.validateInternalToken(token)) {
                filterChain.doFilter(request, response);
            }else{
                rejectRequest(response, "Token invalido");
            }
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
                                r.getPermission()
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