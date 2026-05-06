package com.decor.gateway.security;

import com.decor.gateway.constants.CustomHeaders;
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
                .header(CustomHeaders.TENANT_DB_HEADER, "")
                .header(CustomHeaders.TENANT_USER_HEADER, "")
                .header(CustomHeaders.TENANT_PASSWORD_HEADER, "")
                .header(CustomHeaders.INTERNAL_REQ_HEADER, "")
                .build();

        return chain.filter(exchange.mutate()
                .request(mutatedRequest)
                .build());
    }
}
