package com.kleinkauf.gateway.config;

import com.kleinkauf.gateway.services.TenantService;
import com.kleinkauf.gateway.util.Parser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsProcessor;
import org.springframework.web.cors.reactive.CorsUtils;
import org.springframework.web.cors.reactive.DefaultCorsProcessor;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Objects;
import java.util.regex.Pattern;

/*
Per-tenant CORS: only origins that belong to a tenant are allowed.
Replaces CorsWebFilter because its CorsConfigurationSource is synchronous and forced
a block() inside the event loop; here the origin is validated reactively and
then the same logic as CorsWebFilter is applied.
Runs before the filters that cut the request short (rate limit 429, tenant 404/403, JWT 401): if it ran
after them, those responses would go out without CORS headers and the browser would see a network error, without the status code.
Goes after ClientHeadersFilter (-200), although it only reads the Origin.
 */
@Component
@Order(-150)
@RequiredArgsConstructor
public class CorsConfig implements WebFilter {

    // A store identifier: the same pattern as TenantService.IDENTIFIER in users
    private static final Pattern TENANT_LABEL = Pattern.compile("^[a-z][a-z0-9-]{1,28}[a-z0-9]$");

    private final TenantService tenantService;

    @Value("${platform.url}")
    private String platformUrl;

    private final CorsProcessor processor = new DefaultCorsProcessor();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (!CorsUtils.isCorsRequest(request)) {
            return chain.filter(exchange);
        }

        String origin = Parser.getBaseUrlFromRequest(exchange);
        // The platform frontend (tenant sign-up, superadmin dashboard) is not a tenant.
        // A platform subdomain is also allowed even if the store does not exist or is deleted:
        // TenantGatewayFilter responds 404 and the frontend must be able to read it to show "this store does not exist"
        Mono<Boolean> isAllowed = origin.equals(platformUrl) || isPlatformSubdomain(origin)
                ? Mono.just(true)
                : tenantService.isCorsAllowed(origin);
        return isAllowed
                .map(allowed -> allowed ? allowedConfig(origin) : new CorsConfiguration())
                .flatMap(config -> {
                    boolean isValid = processor.process(config, exchange);
                    if (!isValid || CorsUtils.isPreFlightRequest(request)) {
                        return Mono.empty();
                    }
                    return chain.filter(exchange);
                });
    }

    // <identifier>.<platform host> with the same scheme and port (e.g. http://mitienda.localhost:2999)
    private boolean isPlatformSubdomain(String origin) {
        if (platformUrl == null) {
            return false;
        }
        try {
            URI platform = URI.create(platformUrl);
            URI candidate = URI.create(origin);
            String host = candidate.getHost();
            String suffix = "." + platform.getHost();
            if (host == null || platform.getHost() == null || !host.endsWith(suffix)) {
                return false;
            }
            String label = host.substring(0, host.length() - suffix.length());
            return Objects.equals(platform.getScheme(), candidate.getScheme())
                    && platform.getPort() == candidate.getPort()
                    && TENANT_LABEL.matcher(label).matches();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private CorsConfiguration allowedConfig(String origin) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedOrigin(origin);
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        return config;
    }
}
