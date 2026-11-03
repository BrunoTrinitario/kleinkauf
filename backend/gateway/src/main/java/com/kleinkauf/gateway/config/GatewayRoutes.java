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

                // Link para compartir (Open Graph): la tienda va en la ruta y products no la necesita (ya la
                // resolvio TenantGatewayFilter y llega como X-Forwarded-Host)
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
                .route("internal tenants", r -> r
                        .path("/api/internal/tenants/**")
                        .uri(userUrl))
                // Uso por tenant de products: antes que la ruta general de plataforma (order menor gana)
                .route("platform products", r -> r
                        .order(-1)
                        .path("/api/v1/platform/products/**")
                        .uri(productUrl))
                // Panel de observabilidad de la plataforma: tambien antes que la ruta general de plataforma
                .route("platform observability", r -> r
                        .order(-1)
                        .path("/api/v1/platform/observability/**")
                        .uri(observabilityUrl))
                // Observabilidad y auditoria de la tienda (solo el administrador, lo valida observability)
                .route("observability", r -> r
                        .path("/api/v1/observability/**")
                        .uri(observabilityUrl))
                // WebSocket del panel en tiempo real (se entra con un ticket, ver docs/observabilidad.md)
                .route("observability ws", r -> r
                        .path("/ws")
                        .uri(observabilityWsUrl))
                .route("platform", r -> r
                        .path("/api/v1/platform/**")
                        .uri(userUrl))
                .build();
    }
}
