package com.decor.gateway.security;

import com.decor.gateway.constants.CustomHeaders;
import com.decor.gateway.constants.ErrorMessages;
import com.decor.gateway.services.TenantService;
import com.decor.gateway.util.Parser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.URI;

@Component
@Order(2)
public class TenantGatewayFilter implements WebFilter {

    @Autowired
    private TenantService tenantService;

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return tenantService.getTenant(Parser.getTenantIdentifierFromRequest(exchange))
                .switchIfEmpty(Mono.error(new RuntimeException(ErrorMessages.TENANT_NOT_FOUND)))
                .flatMap(tenant -> {

                    ServerHttpRequest mutatedRequest = exchange.getRequest()
                            .mutate()
                            .header(CustomHeaders.TENANT_DB_HEADER, Parser.getBaseUrlFromRequest(exchange))
                            .header(CustomHeaders.INTERNAL_HOST, tenant.schema())
                            .build();

                    return chain.filter(exchange.mutate()
                            .request(mutatedRequest)
                            .build());

                }).onErrorResume(ex -> {
                    return setError(ex, exchange);
                });
    }

    private Mono<Void> setError(Throwable ex, ServerWebExchange exchange){

        if (ex instanceof WebClientResponseException e) {
            return writeError(exchange, e.getStatusCode(), ex.getMessage());
        }

        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode statusCode = errorResponse.getStatusCode();
            return writeError(
                    exchange,
                    HttpStatus.valueOf(statusCode.value()),
                    ex.getMessage()
            );
        }
        return writeError(exchange, HttpStatus.SERVICE_UNAVAILABLE, "Error de respuesta");
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatusCode status, String message) {
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
