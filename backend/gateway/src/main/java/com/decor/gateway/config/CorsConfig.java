package com.decor.gateway.config;

import com.decor.gateway.services.TenantService;
import com.decor.gateway.util.Parser;
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
CORS por tenant: solo se permiten los origenes que pertenecen a un tenant.
Reemplaza a CorsWebFilter porque su CorsConfigurationSource es sincronico y obligaba
a hacer block() dentro del event loop; aca el origen se valida de forma reactiva y
despues se aplica la misma logica que CorsWebFilter.
Corre antes que los filtros que cortan la request (rate limit 429, tenant 404/403, JWT 401): si corriera
despues, esas respuestas saldrian sin headers CORS y el navegador las veria como un error de red, sin el codigo.
Va despues de ClientHeadersFilter (-200), aunque solo lee el Origin.
 */
@Component
@Order(-150)
@RequiredArgsConstructor
public class CorsConfig implements WebFilter {

    // Identificador de una tienda: el mismo patron que TenantService.IDENTIFIER en users
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
        // El frontend de la plataforma (alta de tenants, panel del superadmin) no es un tenant.
        // Un subdominio de la plataforma tambien se permite aunque la tienda no exista o este borrada:
        // TenantGatewayFilter responde 404 y el frontend tiene que poder leerlo para mostrar "esta tienda no existe"
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

    // <identificador>.<host de la plataforma> con el mismo esquema y puerto (ej. http://mitienda.localhost:2999)
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
