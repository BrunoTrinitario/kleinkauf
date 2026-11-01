package com.decor.product.security;

import com.decor.common.servlet.tenant.TenantFilter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, TenantFilter tenantFilter,HeaderAuthFilter headerAuthFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // Stock: administrador y empleado
                        .requestMatchers("/api/v1/products/*/stock/movements", "/api/v1/products/stock/low")
                            .hasAnyRole("ADMINISTRADOR", "EMPLEADO")
                        // Costo y margen: solo administrador
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/*/pricing").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/products/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/products/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/products/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/bundles/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/bundles/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/bundles/**").hasRole("ADMINISTRADOR")
                        .requestMatchers("/api/v1/products/**").permitAll()
                        .requestMatchers("/api/v1/bundles/**").permitAll()
                        .requestMatchers("/products/images/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        tenantFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                .addFilterBefore(headerAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}