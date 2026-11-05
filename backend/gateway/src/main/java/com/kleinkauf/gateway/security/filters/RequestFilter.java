package com.kleinkauf.gateway.security.filters;

import com.kleinkauf.gateway.factories.RequestTenantFactory;
import com.kleinkauf.gateway.services.RateLimitCounter;
import com.kleinkauf.gateway.util.ManagementRequests;
import com.kleinkauf.gateway.util.Parser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

// Rate limit by IP and origin, in 1-minute windows (images do not count). Counted in Redis (RateLimitCounter)
@Component
@Order(-100)
public class RequestFilter implements WebFilter {

    @Autowired
    private RateLimitCounter rateLimitCounter;
    @Value("${limit.login}")
    private int loginLimit;

    @Value("${limit.signup}")
    private int signupLimit;

    @Value("${limit.global}")
    private int globalLimit;

    @Value("${limit.order}")
    private int orderLimit;

    @Value("${limit.lookup}")
    private int lookupLimit;

    @Value("${limit.receipt}")
    private int receiptLimit;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // The management port is queried by Prometheus and observability every few seconds
        if (ManagementRequests.isManagement(exchange)) {
            return chain.filter(exchange);
        }
        String requestIdentifier = Parser.generateRequestIdentifier(exchange);
        String type = requestIdentifier.split(":")[1];
        if (type.equals(RequestTenantFactory.IMAGE)) {
            return chain.filter(exchange);
        }
        return rateLimitCounter.increment(requestIdentifier).flatMap(count -> {
            if (isExceeded(count, type)){
                exchange.getResponse()
                        .setStatusCode(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
                return exchange.getResponse().setComplete();
            }
            return chain.filter(exchange);
        });
    }

    // count includes the current request: with a limit of 5, 5 pass and the sixth is rejected
    private boolean isExceeded(long count, String type){
        if (type.equals(RequestTenantFactory.LOGIN) && count>loginLimit)
            return true;
        if (type.equals(RequestTenantFactory.SIGNUP) && count>signupLimit)
            return true;
        if (type.equals(RequestTenantFactory.GLOBAL) && count>globalLimit)
            return true;
        if (type.equals(RequestTenantFactory.ORDER) && count>orderLimit)
            return true;
        if (type.equals(RequestTenantFactory.LOOKUP) && count>lookupLimit)
            return true;
        if (type.equals(RequestTenantFactory.RECEIPT) && count>receiptLimit)
            return true;
        return false;
    }
}
