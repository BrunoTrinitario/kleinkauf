package com.kleinkauf.observability.security;

import com.kleinkauf.common.constants.Headers;
import com.kleinkauf.common.constants.TenantConstants;
import com.kleinkauf.common.dto.RoleDto;
import com.kleinkauf.common.security.InternalTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/*
Roles y nombre de un usuario en users, protegido con Resilience4j (instancia "users" en application.properties).
Los superadmins estan en public: sin X-Forwarded-Host users busca ahi.
El fallback esta en @Retry porque es el aspecto mas externo (igual que en sales).
 */
@Slf4j
@Component
public class UsersClient {

    private static final String SERVICE_NAME = "observability";

    @Value("${users.url}")
    private String usersUrl;

    private final InternalTokenService internalTokenService;
    private final RestTemplate restTemplate;

    public UsersClient(InternalTokenService internalTokenService, @Qualifier("usersRestTemplate") RestTemplate restTemplate) {
        this.internalTokenService = internalTokenService;
        this.restTemplate = restTemplate;
    }

    // null si users no respondio
    @CircuitBreaker(name = "users")
    @Retry(name = "users", fallbackMethod = "getUserRolesFallback")
    public List<RoleDto> getUserRoles(Long userId, String tenant) {
        List<RoleDto> roles = restTemplate.exchange(
                usersUrl + "api/internal/users/roles/" + userId,
                HttpMethod.GET,
                new HttpEntity<>(internalHeaders(tenant)),
                new ParameterizedTypeReference<List<RoleDto>>() {}
        ).getBody();
        return roles == null ? List.of() : roles;
    }

    private HttpHeaders internalHeaders(String tenant) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(Headers.INTERNAL_REQUEST, Boolean.toString(true));
        headers.set(Headers.INTERNAL_TOKEN, internalTokenService.generateMicroServiceToken(SERVICE_NAME));
        if (tenant != null && !TenantConstants.PUBLIC.equals(tenant)) {
            headers.set(Headers.FORWARDED_HOST, tenant);
        }
        return headers;
    }

    // Nombre del usuario (para la auditoria). null si no existe o users no respondio
    @CircuitBreaker(name = "users")
    @Retry(name = "users", fallbackMethod = "getUserNameFallback")
    public String getUserName(Long userId, String tenant) {
        JsonNode user = restTemplate.exchange(
                usersUrl + "api/internal/users/" + userId,
                HttpMethod.GET,
                new HttpEntity<>(internalHeaders(tenant)),
                JsonNode.class
        ).getBody();
        return user == null || user.path("name").isMissingNode() ? null : user.path("name").asText(null);
    }

    private String getUserNameFallback(Long userId, String tenant, Throwable ex) {
        log.warn("No se pudo obtener el nombre del usuario {} ({}): {}", userId, tenant, ex.toString());
        return null;
    }

    private List<RoleDto> getUserRolesFallback(Long userId, String tenant, Throwable ex) {
        log.warn("No se pudieron obtener los roles del usuario {} ({}): {}", userId, tenant, ex.toString());
        return null;
    }
}
