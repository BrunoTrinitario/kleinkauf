package com.kleinkauf.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import com.kleinkauf.gateway.util.ManagementRequests;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    // Management port (/health, /ready, /metrics): internal, no login. Goes before the general chain
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

    // JwtFilter, TenantGatewayFilter, etc. are @Component WebFilters: WebFlux registers them on its own
    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
        return http
                .cors(ServerHttpSecurity.CorsSpec::disable)
                .csrf(csrf -> csrf.disable())
                .authorizeExchange(SecurityPassPaths.configureSecurityPaths())
                .build();
    }
}