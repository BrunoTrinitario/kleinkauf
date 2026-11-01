package com.decor.gateway.config;

import com.decor.gateway.services.TenantService;
import com.decor.gateway.util.Parser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

/*
CORS por tenant: solo se permiten los origenes que pertenecen a un tenant.
Reemplaza a CorsWebFilter porque su CorsConfigurationSource es sincronico y obligaba
a hacer block() dentro del event loop; aca el origen se valida de forma reactiva y
despues se aplica la misma logica que CorsWebFilter.
 */
@Component
@RequiredArgsConstructor
public class CorsConfig implements WebFilter {

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
        // El frontend de la plataforma (alta de tenants, panel del superadmin) no es un tenant
        Mono<Boolean> isAllowed = origin.equals(platformUrl)
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

    private CorsConfiguration allowedConfig(String origin) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedOrigin(origin);
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        return config;
    }
}
