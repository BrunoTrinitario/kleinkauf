package com.kleinkauf.gateway.security.filters;

import com.kleinkauf.common.constants.Headers;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;

/*
Primer filtro: borra los headers que solo puede poner el gateway. Si un cliente los manda
(ej. X-User-Id o X-Forwarded-Host) llegarian tal cual a los microservicios, que confian en ellos.
Pone X-Client-Ip con la IP de la conexion, para la auditoria (docs/auditoria.md). Es la de la conexion y no
la de X-Forwarded-For, que la puede inventar el cliente; detras de un proxy habria que tomarla del proxy.
 */
@Component
@Order(-200)
public class ClientHeadersFilter implements WebFilter {

    static final List<String> INTERNAL_HEADERS = List.of(
            Headers.USER_ID,
            Headers.FORWARDED_HOST,
            Headers.OPEN_PATH,
            Headers.TENANT_NAME,
            Headers.CLIENT_IP
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers(headers -> {
                    INTERNAL_HEADERS.forEach(headers::remove);
                    InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
                    // Detras de un proxy (forward-headers-strategy) la direccion viene sin resolver: getAddress() es null
                    if (remote != null) {
                        headers.set(Headers.CLIENT_IP, remote.getHostString());
                    }
                })
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }
}
