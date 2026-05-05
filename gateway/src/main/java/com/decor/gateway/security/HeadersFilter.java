package com.decor.gateway.security;

import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(1)
public class HeadersFilter implements WebFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest mutatedRequest = exchange.getRequest()
                .mutate()
                .header("X-Tenant-DB", "")
                .header("X-Tenant-User", "")
                .header("X-Tenant-Password", "")
                .header("X-Internal-Request", "")
                .build();

        return chain.filter(exchange.mutate()
                .request(mutatedRequest)
                .build());
    }
}
