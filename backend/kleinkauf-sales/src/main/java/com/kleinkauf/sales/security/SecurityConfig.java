package com.kleinkauf.sales.security;

import com.kleinkauf.common.servlet.tenant.TenantFilter;
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
    public SecurityFilterChain filterChain(HttpSecurity http, TenantFilter tenantFilter, HeaderAuthFilter headerAuthFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/internal/**").hasRole("INTERNAL")
                        // Public checkout: guest or logged in (if logged in the order is linked)
                        .requestMatchers(HttpMethod.GET, "/api/v1/sales/checkout").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/sales/orders", "/api/v1/sales/orders/lookup",
                                "/api/v1/sales/orders/*/receipt", "/api/v1/sales/orders/*/cancel").permitAll()
                        // Order management and sales settings: administrator only (not delegable)
                        .requestMatchers("/api/v1/sales/admin/**").hasRole("ADMINISTRADOR")
                        // My orders and the rest: any logged-in user
                        .anyRequest().authenticated()
                )
                .addFilterBefore(tenantFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(headerAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
