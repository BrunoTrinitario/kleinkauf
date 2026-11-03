package com.decor.gateway.security.filters;

import com.decor.common.constants.Headers;
import com.decor.common.dto.TenantDto;
import com.decor.gateway.constants.ErrorMessages;
import com.decor.gateway.constants.PermittedUrls;
import com.decor.gateway.services.TenantService;
import com.decor.gateway.util.ManagementRequests;
import com.decor.gateway.util.Parser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(2)
public class TenantGatewayFilter implements WebFilter {

    public static final String TENANT_ATTRIBUTE = "decor.tenant";

    @Autowired
    private TenantService tenantService;

    @Value("${platform.url:}")
    private String platformUrl;

    /*
    Resuelve el tenant por el Origin y le pasa su schema a los microservicios.
    - Rutas de plataforma (/api/v1/platform/**) e imagenes de productos (/products/images/**): no tienen
      tenant, siguen sin el header (el navegador no manda Origin al cargar un <img>).
    - Tenant inexistente o borrado: 404. Deshabilitado: 403.
    - WS (/ws) desde el frontend de la plataforma: es el panel del superadmin, sin tienda. Desde una tienda
      se resuelve como cualquier request y observability valida que el ticket sea de esa tienda.
    Solo los errores de la busqueda del tenant se responden aca; los de los servicios siguen su curso.
    El schema queda tambien en el atributo TENANT_ATTRIBUTE: lo usa la etiqueta "tenant" de las metricas.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (PermittedUrls.isTenantlessPath(path) || ManagementRequests.isManagement(exchange)
                || isPlatformWebSocket(exchange, path)) {
            return chain.filter(exchange);
        }

        return tenantService.getTenant(Parser.getTenantIdentifierFromRequest(exchange))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, ErrorMessages.TENANT_NOT_FOUND)))
                .flatMap(tenant -> Boolean.TRUE.equals(tenant.enable())
                        ? Mono.just(tenant)
                        : Mono.<TenantDto>error(new ResponseStatusException(HttpStatus.FORBIDDEN, ErrorMessages.TENANT_DISABLED)))
                .onErrorResume(ex -> setError(ex, exchange).then(Mono.empty()))
                .flatMap(tenant -> {
                    exchange.getAttributes().put(TENANT_ATTRIBUTE, tenant.schema());
                    ServerHttpRequest mutatedRequest = exchange.getRequest()
                            .mutate()
                            .header(Headers.FORWARDED_HOST, tenant.schema())
                            .build();

                    return chain.filter(exchange.mutate()
                            .request(mutatedRequest)
                            .build());
                });
    }

    private boolean isPlatformWebSocket(ServerWebExchange exchange, String path) {
        return PermittedUrls.WS_PATH.equals(path) && platformUrl != null && !platformUrl.isBlank()
                && platformUrl.equals(Parser.getBaseUrlFromRequest(exchange));
    }

    private Mono<Void> setError(Throwable ex, ServerWebExchange exchange){

        if (ex instanceof ResponseStatusException e) {
            return writeError(exchange, e.getStatusCode(), e.getReason());
        }

        if (ex instanceof WebClientResponseException e) {
            return writeError(exchange, e.getStatusCode(), ex.getMessage());
        }

        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode statusCode = errorResponse.getStatusCode();
            return writeError(
                    exchange,
                    HttpStatus.valueOf(statusCode.value()),
                    ex.getMessage()
            );
        }
        return writeError(exchange, HttpStatus.SERVICE_UNAVAILABLE, "Error de respuesta");
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatusCode status, String message) {
        exchange.getResponse().setStatusCode(status);
        byte[] bytes = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return exchange.getResponse()
                .writeWith(Mono.just(
                        exchange.getResponse()
                                .bufferFactory()
                                .wrap(bytes)
                ));
    }
}
