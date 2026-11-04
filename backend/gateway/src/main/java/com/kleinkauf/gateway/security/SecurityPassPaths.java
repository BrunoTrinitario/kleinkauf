package com.kleinkauf.gateway.security;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;

public class SecurityPassPaths{
    public static Customizer<ServerHttpSecurity.AuthorizeExchangeSpec> configureSecurityPaths() {
        return ex -> ex
                .pathMatchers("/api/v1/**").permitAll()
                .pathMatchers("/products/images/**").permitAll()
                // Link to share a product (Open Graph): opened by a crawler, without login
                .pathMatchers(org.springframework.http.HttpMethod.GET, "/share/*/products/*").permitAll()
                // The WS carries no JWT: observability opens it with a single-use ticket
                .pathMatchers("/ws").permitAll()
                .anyExchange().authenticated();
    }
}
