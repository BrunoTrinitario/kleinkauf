package com.decor.sales.security;

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
    public SecurityFilterChain filterChain(HttpSecurity http, TenantFilter tenantFilter, HeaderAuthFilter headerAuthFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/internal/**").hasRole("INTERNAL")
                        // Checkout publico: invitado o logueado (si viene logueado el pedido queda asociado)
                        .requestMatchers(HttpMethod.GET, "/api/v1/sales/checkout").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/sales/orders", "/api/v1/sales/orders/lookup",
                                "/api/v1/sales/orders/*/receipt", "/api/v1/sales/orders/*/cancel").permitAll()
                        // Gestion de pedidos y configuracion de ventas: solo el administrador (no se delega)
                        .requestMatchers("/api/v1/sales/admin/**").hasRole("ADMINISTRADOR")
                        // Mis pedidos y el resto: cualquier usuario logueado
                        .anyRequest().authenticated()
                )
                .addFilterBefore(tenantFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(headerAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
