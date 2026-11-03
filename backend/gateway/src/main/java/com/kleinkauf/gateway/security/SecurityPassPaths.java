package com.kleinkauf.gateway.security;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;

public class SecurityPassPaths{
    public static Customizer<ServerHttpSecurity.AuthorizeExchangeSpec> configureSecurityPaths() {
        return ex -> ex
                .pathMatchers("/api/v1/**").permitAll()
                .pathMatchers("/products/images/**").permitAll()
                // Link para compartir un producto (Open Graph): lo abre un crawler, sin login
                .pathMatchers(org.springframework.http.HttpMethod.GET, "/share/*/products/*").permitAll()
                // El WS no lleva JWT: observability lo abre con un ticket de un solo uso
                .pathMatchers("/ws").permitAll()
                .anyExchange().authenticated();
    }
}
