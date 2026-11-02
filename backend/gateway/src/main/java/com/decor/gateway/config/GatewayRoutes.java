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
    @Value("${sales.url}")
    private String salesUrl;


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

                .route("catalog", r -> r
                        .path("/api/v1/catalog/**")
                        .uri(productUrl))

                .route("sales", r -> r
                        .path("/api/v1/sales/**")
                        .uri(salesUrl))

                .route("users", r -> r
                        .path("/api/v1/users/**")
                        .uri(userUrl))
                .route("auth", r -> r
                        .path("/api/v1/auth/**")
                        .uri(userUrl))
                .route("internal tenants", r -> r
                        .path("/api/internal/tenants/**")
                        .uri(userUrl))
                // Uso por tenant de products: antes que la ruta general de plataforma (order menor gana)
                .route("platform products", r -> r
                        .order(-1)
                        .path("/api/v1/platform/products/**")
                        .uri(productUrl))
                .route("platform", r -> r
                        .path("/api/v1/platform/**")
                        .uri(userUrl))
                .build();
    }
}
