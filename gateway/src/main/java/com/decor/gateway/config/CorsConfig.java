package com.decor.gateway.config;

import com.decor.gateway.services.TenantService;
import com.decor.gateway.util.Parser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;

@Configuration
@RequiredArgsConstructor
public class CorsConfig {

    private final TenantService tenantService;

    @Bean
    public CorsWebFilter corsWebFilter() {
        return new CorsWebFilter(exchange -> {
            String origin = Parser.getBaseUrlFromRequest(exchange);
            CorsConfiguration config = new CorsConfiguration();
            if (origin == null || origin.isBlank()) {
                return config;
            }

            boolean allowed = tenantService.getTenantForCorsSync(origin);
            if (!allowed) return config;
            config.setAllowCredentials(true);
            config.addAllowedOrigin(origin);
            config.addAllowedHeader("*");
            config.addAllowedMethod("*");
            return config;
        });
    }
}