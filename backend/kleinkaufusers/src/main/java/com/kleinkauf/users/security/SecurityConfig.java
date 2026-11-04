package com.kleinkauf.users.security;

import com.kleinkauf.common.servlet.tenant.TenantFilter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, HeaderAuthFilter headerAuthFilter, TenantFilter tenantFilter) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/internal/**").permitAll()
                .requestMatchers("/api/v1/users/**").hasRole("ADMINISTRADOR")
                // Plataforma: alta por autoservicio y login del superadmin son publicos
                .requestMatchers(HttpMethod.POST, "/api/v1/platform/tenants", "/api/v1/platform/auth/login",
                        "/api/v1/platform/auth/refresh", "/api/v1/platform/auth/logout").permitAll()
                .requestMatchers("/api/v1/platform/admin/**").hasRole("SUPERADMIN")
                // Reportar un bug desde el footer de una tienda: con o sin sesion
                .requestMatchers(HttpMethod.POST, "/api/v1/bug-reports").permitAll()
                .anyRequest().denyAll()
            ).csrf(csrf -> csrf.disable())
            .headers(headers -> headers
                .frameOptions(frame -> frame.disable())
            ).addFilterBefore(tenantFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(headerAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}