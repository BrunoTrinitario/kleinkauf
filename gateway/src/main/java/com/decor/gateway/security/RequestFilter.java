package com.decor.gateway.security;

import com.decor.gateway.constants.PermittedUrls;
import com.decor.gateway.factories.RequestTenantFactory;
import com.decor.gateway.util.CacheUtil;
import com.decor.gateway.util.Parser;
import com.decor.gateway.util.ResponseUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    @Value("${limit.login}")
    private int loginLimit;

    @Value("${limit.global}")
    private int globalLimit;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
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
        if (type.equals("login") && count>=loginLimit)
            return true;
        if (type.equals("global") && count>=globalLimit)
            return true;
        return false;
    }
}