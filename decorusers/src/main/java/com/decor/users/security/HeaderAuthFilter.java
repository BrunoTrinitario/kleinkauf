package com.decor.users.security;

import com.decor.users.dtos.RoleDto;
import com.decor.users.entities.Role;
import com.decor.users.repositories.RoleRepository;
import com.decor.users.util.CacheUtil;
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
    CacheUtil cacheUtil;

    @Autowired
    RoleRepository roleRepository;


    private final List<String> excludedPaths = List.of(
            "/auth/login",
            "/auth/register",
            "/internal/tenants"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return excludedPaths.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {


        String userIdHeader = request.getHeader("X-User-Id");

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