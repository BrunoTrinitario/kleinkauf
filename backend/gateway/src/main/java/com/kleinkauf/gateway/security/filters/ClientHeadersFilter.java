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
First filter: removes the headers only the gateway may set. If a client sends them
(e.g. X-User-Id or X-Forwarded-Host) they would reach the microservices as is, and those trust them.
Sets X-Client-Ip with the connection's IP, for the audit (docs/features/audit.md). It is the connection's and not
the one in X-Forwarded-For, which the client can make up; behind a proxy it would have to be taken from the proxy.
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
                    // Behind a proxy (forward-headers-strategy) the address comes unresolved: getAddress() is null
                    if (remote != null) {
                        headers.set(Headers.CLIENT_IP, remote.getHostString());
                    }
                })
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }
}
