package com.kleinkauf.gateway;

import com.kleinkauf.gateway.security.filters.UnknownRouteFilter;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// A route the gateway does not route responds 404, not 401
class UnknownRouteFilterTest {

    private final RouteLocator routes = () -> Flux.just(
            route("products", "/api/v1/products/"),
            route("auth", "/api/v1/auth/"));

    private final UnknownRouteFilter filter = new UnknownRouteFilter(routes);

    @Test
    void gatewayRoute_passes() {
        MockServerWebExchange exchange = request("/api/v1/products/5");

        assertTrue(run(exchange));
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void routeToNoService_respondsNotFound() {
        for (String path : java.util.List.of("/inventado", "/api/v1/inventado", "/api/internal/tenants/all")) {
            MockServerWebExchange exchange = request(path);

            assertFalse(run(exchange), path);
            assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode(), path);
        }
    }

    private boolean run(MockServerWebExchange exchange) {
        AtomicBoolean passed = new AtomicBoolean(false);
        filter.filter(exchange, e -> {
            passed.set(true);
            return Mono.empty();
        }).block();
        return passed.get();
    }

    private static MockServerWebExchange request(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get("http://gateway" + path));
    }

    private static Route route(String id, String prefix) {
        return Route.async()
                .id(id)
                .uri("http://localhost")
                .predicate(exchange -> exchange.getRequest().getURI().getPath().startsWith(prefix))
                .build();
    }
}
