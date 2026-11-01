package com.decor.gateway;

import com.decor.gateway.config.CorsConfig;
import com.decor.gateway.services.TenantService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CorsConfigTest {

    private static final String ORIGIN = "http://tienda1.com";

    private final TenantService tenantService = mock(TenantService.class);
    private final CorsConfig filter = new CorsConfig(tenantService);
    private final AtomicBoolean chainCalled = new AtomicBoolean(false);
    private final WebFilterChain chain = exchange -> {
        chainCalled.set(true);
        return Mono.empty();
    };

    @Test
    void origenDeUnTenant_sigueYAgregaLosHeadersCors() {
        when(tenantService.isCorsAllowed(ORIGIN)).thenReturn(Mono.just(true));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://gateway/api/v1/products/all").header(HttpHeaders.ORIGIN, ORIGIN));

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
        assertEquals(ORIGIN, exchange.getResponse().getHeaders().getAccessControlAllowOrigin());
    }

    @Test
    void origenDesconocido_respondeForbiddenYNoSigue() {
        when(tenantService.isCorsAllowed(ORIGIN)).thenReturn(Mono.just(false));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://gateway/api/v1/products/all").header(HttpHeaders.ORIGIN, ORIGIN));

        filter.filter(exchange, chain).block();

        assertFalse(chainCalled.get());
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void preflightDeUnTenant_respondeSinSeguir() {
        when(tenantService.isCorsAllowed(ORIGIN)).thenReturn(Mono.just(true));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options("http://gateway/api/v1/products/all")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name()));

        filter.filter(exchange, chain).block();

        assertFalse(chainCalled.get());
        assertEquals(ORIGIN, exchange.getResponse().getHeaders().getAccessControlAllowOrigin());
    }

    @Test
    void sinOrigin_noEsCorsYNoConsultaTenants() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://gateway/api/v1/products/all"));

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
        verifyNoInteractions(tenantService);
    }
}
