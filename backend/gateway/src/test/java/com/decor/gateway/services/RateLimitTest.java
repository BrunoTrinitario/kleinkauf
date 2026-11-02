package com.decor.gateway.services;

import com.decor.gateway.config.CaffeineConfig;
import com.decor.gateway.factories.RequestTenantFactory;
import com.decor.gateway.security.filters.RequestFilter;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RateLimitTest {

    private final LocalCacheService localCacheService = new LocalCacheService();
    private final RequestFilter filter = new RequestFilter();

    RateLimitTest() {
        CaffeineConfig config = new CaffeineConfig();
        ReflectionTestUtils.setField(localCacheService, "rateLimitCache", config.rateLimitedCache());
        ReflectionTestUtils.setField(localCacheService, "tenantCache", config.tenantCache());
        localCacheService.setClock(Clock.fixed(Instant.parse("2026-11-01T10:00:10Z"), ZoneOffset.UTC));
        ReflectionTestUtils.setField(filter, "localCacheService", localCacheService);
        ReflectionTestUtils.setField(filter, "loginLimit", 5);
        ReflectionTestUtils.setField(filter, "signupLimit", 3);
        ReflectionTestUtils.setField(filter, "globalLimit", 300);
    }

    @Test
    void elContadorSumaYSeReiniciaEnElMinutoSiguiente() {
        assertEquals(1, localCacheService.rateLimitCache("rate_limit:global:x"));
        assertEquals(2, localCacheService.rateLimitCache("rate_limit:global:x"));
        assertEquals(1, localCacheService.rateLimitCache("rate_limit:global:otro"));

        localCacheService.setClock(Clock.fixed(Instant.parse("2026-11-01T10:01:00Z"), ZoneOffset.UTC));

        assertEquals(1, localCacheService.rateLimitCache("rate_limit:global:x"));
    }

    @Test
    void altaDeTenants_permite3PorMinuto() {
        for (int i = 0; i < 3; i++) {
            assertNull(statusOf("POST", "/api/v1/platform/tenants"), "request " + (i + 1));
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, statusOf("POST", "/api/v1/platform/tenants"));
    }

    @Test
    void login_permite5PorMinuto() {
        for (int i = 0; i < 5; i++) {
            assertNull(statusOf("POST", "/api/v1/auth/login"), "request " + (i + 1));
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, statusOf("POST", "/api/v1/auth/login"));
        // El login de la plataforma comparte el limite de login
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, statusOf("POST", "/api/v1/platform/auth/login"));
    }

    @Test
    void lasImagenesNoCuentan() {
        for (int i = 0; i < 400; i++) {
            assertNull(statusOf("GET", "/products/images/foto.jpg"));
        }
        assertNull(statusOf("GET", "/api/v1/products/all"));
    }

    @Test
    void tiposDeRuta() {
        assertEquals(RequestTenantFactory.SIGNUP, RequestTenantFactory.generatePathType("/api/v1/platform/tenants"));
        assertEquals(RequestTenantFactory.GLOBAL, RequestTenantFactory.generatePathType("/api/v1/platform/admin/tenants"));
        assertEquals(RequestTenantFactory.LOGIN, RequestTenantFactory.generatePathType("/api/v1/platform/auth/login"));
        assertEquals(RequestTenantFactory.IMAGE, RequestTenantFactory.generatePathType("/products/images/a.jpg"));
    }

    // null = paso (no se seteo status)
    private HttpStatus statusOf(String method, String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.method(org.springframework.http.HttpMethod.valueOf(method), "http://gateway" + path)
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .remoteAddress(new InetSocketAddress("10.0.0.1", 5000)));
        filter.filter(exchange, e -> Mono.empty()).block();
        return (HttpStatus) exchange.getResponse().getStatusCode();
    }
}
