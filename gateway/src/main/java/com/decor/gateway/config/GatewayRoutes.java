package com.decor.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRoutes {

    @Value("${users.url}")
    private String userUrl;
    @Value("${products.url}")
    private String productUrl;


    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("products", r -> r
                        .path("/api/v1/products/**")
                        .uri(productUrl))

                .route("products", r -> r
                        .path("/products/images/**")
                        .uri(productUrl))

                .route("bundles", r -> r
                        .path("/api/v1/bundles/**")
                        .uri(productUrl))

                .route("users", r -> r
                        .path("/api/v1/users/**")
                        .uri(userUrl))
                .route("auth", r -> r
                        .path("/api/v1/auth/**")
                        .uri(userUrl))
                .route("internal tenants", r -> r
                        .path("/api/internal/tenants/**")
                        .uri(userUrl))
                .build();
    }
}
