package com.kleinkauf.sales.services;

import com.kleinkauf.common.constants.Headers;
import com.kleinkauf.common.dto.RoleDto;
import com.kleinkauf.common.dto.TenantDto;
import com.kleinkauf.common.security.InternalTokenService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/*
HTTP calls to the users microservice, protected with Resilience4j
("users" instance in application.properties):
- Retry: retries network / 5xx errors.
- CircuitBreaker: if users keeps failing, it stops calling it for a while and goes straight to the fallback.
The fallback is on @Retry because it is the outermost aspect; if it were on
@CircuitBreaker the retry would never see the exception.
 */
@Slf4j
@Component
public class UsersClient {

    private static final String SERVICE_NAME = "sales";

    @Value("${users.url}")
    private String usersUrl;

    @Autowired
    private InternalTokenService internalTokenService;

    @Autowired
    private RestTemplate restTemplate;

    @CircuitBreaker(name = "users")
    @Retry(name = "users", fallbackMethod = "getUserRolesFallback")
    public List<RoleDto> getUserRoles(Long userId, String tenant) {
        HttpHeaders headers = internalHeaders();
        headers.set(Headers.FORWARDED_HOST, tenant);
        return restTemplate.exchange(
                usersUrl + "api/internal/users/roles/" + userId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<RoleDto>>() {}
        ).getBody();
    }

    @CircuitBreaker(name = "users")
    @Retry(name = "users", fallbackMethod = "getTenantsFallback")
    public List<TenantDto> getTenants() {
        return restTemplate.exchange(
                usersUrl + "api/internal/tenants/all",
                HttpMethod.GET,
                new HttpEntity<>(internalHeaders()),
                new ParameterizedTypeReference<List<TenantDto>>() {}
        ).getBody();
    }

    @CircuitBreaker(name = "users")
    @Retry(name = "users", fallbackMethod = "getTenantFallback")
    public TenantDto getTenant(String schema) {
        return restTemplate.exchange(
                usersUrl + "api/internal/tenants/id?schema=" + schema,
                HttpMethod.GET,
                new HttpEntity<>(internalHeaders()),
                TenantDto.class
        ).getBody();
    }

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(Headers.INTERNAL_REQUEST, Boolean.toString(true));
        headers.set(Headers.INTERNAL_TOKEN, internalTokenService.generateMicroServiceToken(SERVICE_NAME));
        return headers;
    }

    // Without roles the user stays authenticated but only accesses public content
    private List<RoleDto> getUserRolesFallback(Long userId, String tenant, Throwable ex) {
        log.warn("No se pudieron obtener los roles del usuario {} ({}): {}", userId, tenant, ex.toString());
        return null;
    }

    private List<TenantDto> getTenantsFallback(Throwable ex) {
        log.warn("No se pudieron obtener los tenants: {}", ex.toString());
        return null;
    }

    private TenantDto getTenantFallback(String schema, Throwable ex) {
        log.warn("No se pudo obtener el tenant {}: {}", schema, ex.toString());
        return null;
    }
}
