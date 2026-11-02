package com.decor.gateway.security.filters;

import com.decor.gateway.factories.RequestTenantFactory;
import com.decor.gateway.services.LocalCacheService;
import com.decor.gateway.util.Parser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

// Rate limit por IP y origen, en ventanas de 1 minuto (las imagenes no cuentan)
@Component
@Order(-100)
public class RequestFilter implements WebFilter {

    @Autowired
    private LocalCacheService localCacheService;
    @Value("${limit.login}")
    private int loginLimit;

    @Value("${limit.signup}")
    private int signupLimit;

    @Value("${limit.global}")
    private int globalLimit;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestIdentifier = Parser.generateRequestIdentifier(exchange);
        String type = requestIdentifier.split(":")[1];
        if (type.equals(RequestTenantFactory.IMAGE)) {
            return chain.filter(exchange);
        }
        long count = localCacheService.rateLimitCache(requestIdentifier);
        if (isExceeded(count, type)){
            exchange.getResponse()
                    .setStatusCode(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    // count incluye la request actual: con limite 5 pasan 5 y la sexta se rechaza
    private boolean isExceeded(long count, String type){
        if (type.equals(RequestTenantFactory.LOGIN) && count>loginLimit)
            return true;
        if (type.equals(RequestTenantFactory.SIGNUP) && count>signupLimit)
            return true;
        if (type.equals(RequestTenantFactory.GLOBAL) && count>globalLimit)
            return true;
        return false;
    }
}
