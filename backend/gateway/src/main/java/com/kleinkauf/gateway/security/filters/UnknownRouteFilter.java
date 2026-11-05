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
Routes the gateway does not route to any service: 404. Without this they responded 401 (Spring Security or JwtFilter ask for
login before the gateway looks up the route) or 404 for lack of a store, depending on the route and the headers.
It only looks at the gateway's routes (GatewayRoutes): an unknown endpoint inside a service
(e.g. /api/v1/products/made-up) still asks for login, so each service's endpoints cannot be enumerated.
Runs after CORS (-150), so the browser can read the 404, and before Spring Security (-100).
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
