package com.decor.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final TenantGatewayFilter tenantGatewayFilter;

    public SecurityConfig(JwtFilter jwtFilter,  TenantGatewayFilter tenantGatewayFilter) {

        this.jwtFilter = jwtFilter;
        this.tenantGatewayFilter = tenantGatewayFilter;
    }

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {

        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .authorizeExchange(ex -> ex
                        .pathMatchers("/api/v1/**").permitAll()
                        .pathMatchers("/products/images/**").permitAll()
                        .anyExchange().authenticated()
                )
                .build();
    }
}