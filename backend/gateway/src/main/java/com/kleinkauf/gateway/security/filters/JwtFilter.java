package com.kleinkauf.gateway.security.filters;

import com.kleinkauf.common.constants.Headers;
import com.kleinkauf.common.constants.TenantConstants;
import com.kleinkauf.common.constants.TokenConstants;
import com.kleinkauf.gateway.constants.PermittedUrls;
import com.kleinkauf.gateway.util.JwtUtil;
import com.kleinkauf.gateway.util.ManagementRequests;
import com.kleinkauf.gateway.util.ResponseUtil;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(3)
public class JwtFilter implements WebFilter {

    private final JwtUtil jwtUtil;

    public JwtFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        // Management port (/health, /ready, /metrics): internal, no login
        if (ManagementRequests.isManagement(exchange)) {
            return chain.filter(exchange);
        }

        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getPath();

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (PermittedUrls.isPermitted(method, path) || method.equals("OPTIONS")) {
            // Optional login (e.g. creating an order): with a token it is validated just like on private routes
            if (PermittedUrls.isOptionalAuth(method, path) && authHeader != null && authHeader.startsWith("Bearer ")) {
                return authenticate(exchange, chain, path, authHeader.substring(7));
            }
            ServerHttpRequest mutatedRequest = exchange.getRequest()
                    .mutate()
                    .header(Headers.OPEN_PATH, "true")
                    .build();
            return chain.filter(exchange.mutate()
                    .request(mutatedRequest)
                    .build()
            );
        }

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseUtil.unauthorized(exchange);
        }

        return authenticate(exchange, chain, path, authHeader.substring(7));
    }

    // Validates the token and passes the user to the microservice in X-User-Id
    private Mono<Void> authenticate(ServerWebExchange exchange, WebFilterChain chain, String path, String token) {
        try {
            if (!jwtUtil.validateToken(token)) {
                return ResponseUtil.unauthorized(exchange);
            }

            // Only the session token gets in: neither the recovery email one nor the refresh token (see TokenConstants).
            // No type: session tokens from before the claim, which expire on their own
            String type = jwtUtil.getType(token);
            if (type != null && !TokenConstants.ACCESS.equals(type)) {
                return ResponseUtil.unauthorized(exchange);
            }

            // User ids repeat across tenants: the token must belong to the request's tenant
            if (!expectedTenant(exchange, path).equals(jwtUtil.getTenant(token))) {
                return ResponseUtil.unauthorized(exchange);
            }

            Long userId = jwtUtil.getUserId(token);

            Authentication auth = new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    null
            );

            ServerHttpRequest mutatedRequest = exchange.getRequest()
                    .mutate()
                    .header(Headers.USER_ID, String.valueOf(userId))
                    .build();

            ServerWebExchange mutatedExchange = exchange.mutate()
                    .request(mutatedRequest)
                    .build();

            return chain.filter(mutatedExchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));

        } catch (Exception e) {
            return ResponseUtil.unauthorized(exchange);
        }
    }

    // Platform: public. Store: the schema set by TenantGatewayFilter (runs before, Order 2)
    private String expectedTenant(ServerWebExchange exchange, String path) {
        if (PermittedUrls.isPlatformPath(path)) {
            return TenantConstants.PUBLIC;
        }
        String schema = exchange.getRequest().getHeaders().getFirst(Headers.FORWARDED_HOST);
        return schema != null ? schema : "";
    }
}