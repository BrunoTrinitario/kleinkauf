package com.kleinkauf.gateway.security.filters;

import com.kleinkauf.common.constants.Headers;
import com.kleinkauf.common.dto.TenantDto;
import com.kleinkauf.gateway.constants.ErrorMessages;
import com.kleinkauf.gateway.constants.PermittedUrls;
import com.kleinkauf.gateway.services.TenantService;
import com.kleinkauf.gateway.util.ManagementRequests;
import com.kleinkauf.gateway.util.Parser;
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

import java.net.URI;
import java.util.regex.Matcher;

@Component
@Order(2)
public class TenantGatewayFilter implements WebFilter {

    public static final String TENANT_ATTRIBUTE = "kleinkauf.tenant";

    @Autowired
    private TenantService tenantService;

    @Value("${platform.url:}")
    private String platformUrl;

    /*
    Resolves the tenant by the Origin and passes its schema on to the microservices.
    - Platform routes (/api/v1/platform/**) and product images (/products/images/**): they have no
      tenant, they go on without the header (the browser does not send Origin when loading an <img>).
    - Unknown or deleted tenant: 404. Disabled: 403.
    - WS (/ws) from the platform frontend: it is the superadmin dashboard, with no store. From a store
      it is resolved like any request and observability checks that the ticket belongs to that store.
    Only tenant lookup errors are answered here; the services' errors run their course.
    The schema is also kept in the TENANT_ATTRIBUTE attribute: the metrics' "tenant" tag uses it.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (PermittedUrls.isTenantlessPath(path) || ManagementRequests.isManagement(exchange)
                || isPlatformWebSocket(exchange, path)) {
            return chain.filter(exchange);
        }

        return tenantService.getTenant(tenantOrigin(exchange, path))
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

    /*
    The store URL used to look it up: the Origin, except for the share link, which a crawler opens without
    Origin. There the store comes from the identifier in the path, with the scheme, host and port of platform.url
    (http://localhost:2999 + mitienda -> http://mitienda.localhost:2999, the way users builds the URL on sign-up).
     */
    private String tenantOrigin(ServerWebExchange exchange, String path) {
        Matcher share = PermittedUrls.SHARE_PATH.matcher(path);
        if (!share.matches() || platformUrl == null || platformUrl.isBlank()) {
            return Parser.getTenantIdentifierFromRequest(exchange);
        }
        URI platform = URI.create(platformUrl);
        String port = platform.getPort() == -1 ? "" : ":" + platform.getPort();
        return platform.getScheme() + "://" + share.group(1) + "." + platform.getHost() + port;
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
