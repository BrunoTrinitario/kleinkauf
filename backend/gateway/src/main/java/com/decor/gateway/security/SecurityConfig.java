package com.decor.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import com.decor.gateway.util.ManagementRequests;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    // Puerto de gestion (/health, /ready, /metrics): interno, sin login. Va antes que la cadena general
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityWebFilterChain managementFilterChain(ServerHttpSecurity http) {
        return http
                .securityMatcher(exchange -> ManagementRequests.isManagement(exchange)
                        ? ServerWebExchangeMatcher.MatchResult.match()
                        : ServerWebExchangeMatcher.MatchResult.notMatch())
                .csrf(csrf -> csrf.disable())
                .authorizeExchange(ex -> ex.anyExchange().permitAll())
                .build();
    }

    // JwtFilter, TenantGatewayFilter, etc. son WebFilter @Component: WebFlux los registra solos
    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
        return http
                .cors(ServerHttpSecurity.CorsSpec::disable)
                .csrf(csrf -> csrf.disable())
                .authorizeExchange(SecurityPassPaths.configureSecurityPaths())
                .build();
    }
}