package com.decor.gateway.security;

import com.decor.gateway.dtos.ComTenantDto;
import com.decor.gateway.services.TenantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.URI;

@Component
public class TenantGatewayFilter implements WebFilter {

    @Autowired
    private TenantService tenantService;

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        URI uri = exchange.getRequest().getURI();

        String baseUrl = uri.getScheme() + "://" + uri.getAuthority();

        return tenantService.getTenant(baseUrl)
                .switchIfEmpty(Mono.error(new RuntimeException("Tenant not found")))
                .flatMap(tenant -> {

                    ServerHttpRequest mutatedRequest = exchange.getRequest()
                            .mutate()
                            .header("X-Tenant-DB", tenant.dbName())
                            .header("X-Tenant-User", tenant.dbUser())
                            .header("X-Tenant-Password", tenant.dbPassword())
                            .build();

                    return chain.filter(exchange.mutate()
                            .request(mutatedRequest)
                            .build());
                }).onErrorResume(ex -> {

                    if (ex instanceof WebClientResponseException e) {
                        return writeError(exchange, e.getStatusCode(), "Error de respuesta");
                    }

                    return writeError(exchange, HttpStatus.SERVICE_UNAVAILABLE, "Error de respuesta");
                });
    }

    private Mono<Void> writeError(ServerWebExchange exchange,
                                  HttpStatusCode status,
                                  String message) {

        exchange.getResponse().setStatusCode(status);

        byte[] bytes = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        return exchange.getResponse()
                .writeWith(Mono.just(
                        exchange.getResponse()
                                .bufferFactory()
                                .wrap(bytes)
                ));
    }
}
