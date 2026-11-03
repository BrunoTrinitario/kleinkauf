package com.decor.observability.security;

import com.decor.common.constants.Headers;
import com.decor.common.constants.TenantConstants;
import com.decor.common.dto.RoleDto;
import com.decor.common.security.InternalTokenService;
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
Roles de un usuario en users, protegido con Resilience4j (instancia "users" en application.properties).
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
        HttpHeaders headers = new HttpHeaders();
        headers.set(Headers.INTERNAL_REQUEST, Boolean.toString(true));
        headers.set(Headers.INTERNAL_TOKEN, internalTokenService.generateMicroServiceToken(SERVICE_NAME));
        if (tenant != null && !TenantConstants.PUBLIC.equals(tenant)) {
            headers.set(Headers.FORWARDED_HOST, tenant);
        }
        List<RoleDto> roles = restTemplate.exchange(
                usersUrl + "api/internal/users/roles/" + userId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<RoleDto>>() {}
        ).getBody();
        return roles == null ? List.of() : roles;
    }

    private List<RoleDto> getUserRolesFallback(Long userId, String tenant, Throwable ex) {
        log.warn("No se pudieron obtener los roles del usuario {} ({}): {}", userId, tenant, ex.toString());
        return null;
    }
}
