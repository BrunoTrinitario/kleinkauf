package com.decor.gateway.security.filters;

import com.decor.common.constants.Headers;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/*
Primer filtro: borra los headers que solo puede poner el gateway. Si un cliente los manda
(ej. X-User-Id o X-Forwarded-Host) llegarian tal cual a los microservicios, que confian en ellos.
 */
@Component
@Order(-200)
public class ClientHeadersFilter implements WebFilter {

    static final List<String> INTERNAL_HEADERS = List.of(
            Headers.USER_ID,
            Headers.FORWARDED_HOST,
            Headers.OPEN_PATH,
            Headers.TENANT_NAME
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers(headers -> INTERNAL_HEADERS.forEach(headers::remove))
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }
}
