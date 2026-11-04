package com.kleinkauf.gateway.security.filters;

import com.kleinkauf.gateway.util.ManagementRequests;
import com.kleinkauf.gateway.util.ResponseUtil;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/*
Rutas que el gateway no rutea a ningun servicio: 404. Sin esto respondian 401 (Spring Security o JwtFilter piden
login antes de que el gateway busque la ruta) o 404 por falta de tienda, segun la ruta y los headers.
Solo mira las rutas del gateway (GatewayRoutes): un endpoint inexistente dentro de un servicio
(ej. /api/v1/products/inventado) sigue pidiendo login, asi no se pueden listar los endpoints de cada servicio.
Corre despues de CORS (-150), para que el navegador pueda leer el 404, y antes de Spring Security (-100).
 */
@Component
@Order(-120)
public class UnknownRouteFilter implements WebFilter {

    private final RouteLocator routeLocator;

    public UnknownRouteFilter(RouteLocator routeLocator) {
        this.routeLocator = routeLocator;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (ManagementRequests.isManagement(exchange)) {
            return chain.filter(exchange);
        }
        return routeLocator.getRoutes()
                .concatMap(route -> Mono.from(route.getPredicate().apply(exchange)))
                .any(Boolean::booleanValue)
                .flatMap(found -> found ? chain.filter(exchange) : ResponseUtil.notFound(exchange));
    }
}
