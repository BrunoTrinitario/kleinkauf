package com.kleinkauf.gateway.config;

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
    @Value("${observability.url}")
    private String observabilityUrl;
    @Value("${observability.ws-url}")
    private String observabilityWsUrl;


    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("products", r -> r
                        .path("/api/v1/products/**")
                        .uri(productUrl))

                .route("products", r -> r
                        .path("/products/images/**")
                        .uri(productUrl))

                // Share link (Open Graph): the store goes in the path and products does not need it (TenantGatewayFilter
                // already resolved it and it arrives as X-Forwarded-Host)
                .route("share", r -> r
                        .path("/share/*/products/*")
                        .filters(f -> f.rewritePath("/share/[^/]+/products/(?<slug>[^/]+)", "/share/products/${slug}"))
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
                // Reporting a bug from a store's footer (read by the superadmins)
                .route("bug reports", r -> r
                        .path("/api/v1/bug-reports")
                        .uri(userUrl))
                // Per-tenant usage from products: before the general platform route (lower order wins)
                .route("platform products", r -> r
                        .order(-1)
                        .path("/api/v1/platform/products/**")
                        .uri(productUrl))
                // Platform observability dashboard: also before the general platform route
                .route("platform observability", r -> r
                        .order(-1)
                        .path("/api/v1/platform/observability/**")
                        .uri(observabilityUrl))
                // Store observability and audit (administrator only, validated by observability)
                .route("observability", r -> r
                        .path("/api/v1/observability/**")
                        .uri(observabilityUrl))
                // Real-time dashboard WebSocket (entered with a ticket, see docs/observabilidad.md)
                .route("observability ws", r -> r
                        .path("/ws")
                        .uri(observabilityWsUrl))
                .route("platform", r -> r
                        .path("/api/v1/platform/**")
                        .uri(userUrl))
                .build();
    }
}
