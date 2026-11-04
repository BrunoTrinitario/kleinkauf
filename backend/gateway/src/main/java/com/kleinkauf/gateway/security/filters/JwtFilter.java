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

        // Puerto de gestion (/health, /ready, /metrics): interno, sin login
        if (ManagementRequests.isManagement(exchange)) {
            return chain.filter(exchange);
        }

        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getPath();

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (PermittedUrls.isPermitted(method, path) || method.equals("OPTIONS")) {
            // Login opcional (ej. crear un pedido): con token se valida igual que en las rutas privadas
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

    // Valida el token y pasa el usuario al microservicio en X-User-Id
    private Mono<Void> authenticate(ServerWebExchange exchange, WebFilterChain chain, String path, String token) {
        try {
            if (!jwtUtil.validateToken(token)) {
                return ResponseUtil.unauthorized(exchange);
            }

            // Solo se entra con el token de sesion: ni el del mail de recuperacion ni el refresh (ver TokenConstants).
            // Sin tipo: los de sesion anteriores al claim, que vencen solos
            String type = jwtUtil.getType(token);
            if (type != null && !TokenConstants.ACCESS.equals(type)) {
                return ResponseUtil.unauthorized(exchange);
            }

            // Los ids de usuario se repiten entre tenants: el token tiene que ser del tenant de la request
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

    // Plataforma: public. Tienda: el schema que puso TenantGatewayFilter (corre antes, Order 2)
    private String expectedTenant(ServerWebExchange exchange, String path) {
        if (PermittedUrls.isPlatformPath(path)) {
            return TenantConstants.PUBLIC;
        }
        String schema = exchange.getRequest().getHeaders().getFirst(Headers.FORWARDED_HOST);
        return schema != null ? schema : "";
    }
}