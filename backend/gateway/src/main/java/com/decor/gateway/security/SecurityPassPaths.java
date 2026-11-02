package com.decor.gateway.security;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;

public class SecurityPassPaths{
    public static Customizer<ServerHttpSecurity.AuthorizeExchangeSpec> configureSecurityPaths() {
        return ex -> ex
                .pathMatchers("/api/v1/**").permitAll()
                .pathMatchers("/products/images/**").permitAll()
                .anyExchange().authenticated();
    }
}
