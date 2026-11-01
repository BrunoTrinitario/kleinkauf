package com.decor.gateway.security.filters;

import com.decor.gateway.constants.PermittedUrls;
import com.decor.common.security.InternalTokenService;
import com.decor.gateway.util.Parser;
import com.decor.gateway.util.ResponseUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(1)
public class MsFilter implements WebFilter {

    @Autowired
    InternalTokenService internalTokenService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        String path = exchange.getRequest().getURI().getPath();
        if (path.startsWith(PermittedUrls.INTERNAL_PATH)) {
            String token = Parser.getInternalTokenFromRequest(exchange);
            if (internalTokenService.validateInternalToken(token)) {
                return chain.filter(exchange);
            }else{
                return ResponseUtil.unauthorized(exchange);
            }
        }else{
            return chain.filter(exchange);
        }
    }


}
