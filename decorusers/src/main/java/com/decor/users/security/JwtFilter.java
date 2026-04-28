package com.decor.users.security;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import com.decor.users.entities.Role;
import com.decor.users.repositories.RoleRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.decor.users.entities.User;
import com.decor.users.repositories.UserRepository;
import com.decor.users.util.JwtUtil;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    public JwtFilter(JwtUtil jwtUtil, RoleRepository roleRepository, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        return path.startsWith("/api/auth/register")
            || path.startsWith("/h2-console/")
            || path.startsWith("/api/auth/login");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        
        if (shouldNotFilter(request)){
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {

            String token = header.substring(7);

            if (!jwtUtil.validateToken(token)) {
                return;
            }

            Claims claims = jwtUtil.getClaims(token);

            Long userId = claims.get("id", Long.class);

            User user = userRepository.findById(userId)
                    .orElse(null);

            if (user != null) {

                Set<String> roles = new HashSet<>();
                for  (Role role : roleRepository.findByUser_Id(userId)) {
                    roles.add(role.getType());
                }

                CustomUserDetails userDetails = new CustomUserDetails(user,roles);

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                SecurityContextHolder.getContext().setAuthentication(auth);
            }

        }

        filterChain.doFilter(request, response);
    }
}