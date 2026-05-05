package com.decor.gateway.security;

import com.decor.gateway.constants.PermittedUrls;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(-1)
public class MsFilter implements WebFilter {

    @Value("${api.internal.key}")
    private String apiInternalKey;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (path.startsWith(PermittedUrls.INTERNAL_PATH)) {
            String msIdentfier = exchange.getRequest()
                    .getHeaders()
                    .getFirst("X-Internal-Key");
            if (msIdentfier.equals(apiInternalKey)) {
                return chain.filter(exchange);
            }else{
                return unauthorized(exchange);
            }
        }else{
            return chain.filter(exchange);
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
