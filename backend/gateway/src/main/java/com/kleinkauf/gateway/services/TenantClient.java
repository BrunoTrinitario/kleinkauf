package com.kleinkauf.gateway.services;

import com.kleinkauf.common.constants.Headers;
import com.kleinkauf.common.dto.TenantDto;
import com.kleinkauf.common.security.InternalTokenService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/*
Calls to the users microservice to resolve tenants, protected with
Resilience4j ("users" instance in application.properties) and with a timeout.
The fallback goes on @Retry because it is the outermost aspect.
 */
@Slf4j
@Component
public class TenantClient {

    private static final String SERVICE_NAME = "gateway";

    @Value("${tenant.url}")
    private String tenantUrl;

    @Value("${http.client.timeout:3s}")
    private Duration timeout;

    @Autowired
    private InternalTokenService internalTokenService;

    private final WebClient webClient;

    public TenantClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @CircuitBreaker(name = "users")
    @Retry(name = "users")
    public Mono<TenantDto> getTenantByUrl(String url) {
        return webClient.get()
                .uri(tenantUrl + "/id?url=" + url)
                .header(Headers.INTERNAL_REQUEST, "true")
                .header(Headers.INTERNAL_TOKEN, internalTokenService.generateMicroServiceToken(SERVICE_NAME))
                .retrieve()
                .bodyToMono(TenantDto.class)
                .timeout(timeout);
    }

    @CircuitBreaker(name = "users")
    @Retry(name = "users", fallbackMethod = "isCorsAllowedFallback")
    public Mono<Boolean> isCorsAllowed(String url) {
        return webClient.get()
                .uri(tenantUrl + "/cors?url=" + url)
                .header(Headers.INTERNAL_REQUEST, "true")
                .header(Headers.INTERNAL_TOKEN, internalTokenService.generateMicroServiceToken(SERVICE_NAME))
                .retrieve()
                .bodyToMono(Boolean.class)
                .timeout(timeout);
    }

    // If users does not answer, the origin is not allowed
    private Mono<Boolean> isCorsAllowedFallback(String url, Throwable ex) {
        log.warn("No se pudo validar CORS para {}: {}", url, ex.toString());
        return Mono.just(false);
    }
}
