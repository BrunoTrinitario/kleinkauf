package com.decor.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRoutes {

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("products", r -> r
                        .path("/api/v1/products/**")
                        .uri("http://localhost:3002"))

                .route("products", r -> r
                        .path("/products/images/**")
                        .uri("http://localhost:3002"))

                .route("users", r -> r
                        .path("/api/v1/users/**")
                        .uri("http://localhost:3001"))
                .route("auth", r -> r
                        .path("/api/v1/auth/**")
                        .uri("http://localhost:3001"))
                .build();
    }
}
