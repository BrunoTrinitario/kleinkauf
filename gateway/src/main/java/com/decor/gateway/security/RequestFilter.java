package com.decor.gateway.security;

import com.decor.gateway.constants.PermittedUrls;
import com.decor.gateway.factories.RequestTenantFactory;
import com.decor.gateway.util.CacheUtil;
import com.decor.gateway.util.Parser;
import com.decor.gateway.util.ResponseUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(-100)
public class RequestFilter implements WebFilter {

    @Autowired
    private CacheUtil cacheUtil;
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 1. Obtener tenant, IP y path
        // 2. Incrementar contadores en Redis
        // 3. Verificar límites
        // 4. Si excede -> 429
        // 5. Si no -> chain.filter(exchange)
        String requestIdentifier = Parser.generateRequestIdentifier(exchange);
        long count = cacheUtil.requestCounter(requestIdentifier);
        if (isExceeded(count,requestIdentifier.split(":")[1])){
            exchange.getResponse()
                    .setStatusCode(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    private boolean isExceeded(long count, String type){
        if (type.equals("login") && count>=5)
            return true;
        if (type.equals("global") && count>=30)
            return true;
        return false;
    }
}