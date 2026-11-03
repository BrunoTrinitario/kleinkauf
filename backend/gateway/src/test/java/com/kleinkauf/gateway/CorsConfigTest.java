package com.kleinkauf.gateway;

import com.kleinkauf.gateway.config.CorsConfig;
import com.kleinkauf.gateway.services.TenantService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
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
    void frontendDeLaPlataforma_sePermiteSinBuscarTenant() {
        ReflectionTestUtils.setField(filter, "platformUrl", "http://localhost:3000");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("http://gateway/api/v1/platform/tenants").header(HttpHeaders.ORIGIN, "http://localhost:3000"));

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
        assertEquals("http://localhost:3000", exchange.getResponse().getHeaders().getAccessControlAllowOrigin());
        verifyNoInteractions(tenantService);
    }

    @Test
    void subdominioDeLaPlataforma_sePermiteAunqueLaTiendaNoExista() {
        // TenantGatewayFilter responde 404 despues: el navegador tiene que poder leerlo
        ReflectionTestUtils.setField(filter, "platformUrl", "http://localhost:2999");
        String origin = "http://no-existe.localhost:2999";
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://gateway/api/v1/catalog/products").header(HttpHeaders.ORIGIN, origin));

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
        assertEquals(origin, exchange.getResponse().getHeaders().getAccessControlAllowOrigin());
        verifyNoInteractions(tenantService);
    }

    @Test
    void otroPuertoOEsquemaOIdentificadorInvalido_noEsSubdominioDeLaPlataforma() {
        ReflectionTestUtils.setField(filter, "platformUrl", "http://localhost:2999");
        for (String origin : new String[]{
                "http://tienda.localhost:3000",
                "https://tienda.localhost:2999",
                "http://a.localhost:2999",
                "http://1tienda.localhost:2999",
                "http://tienda.otro.localhost:2999",
                "http://tienda.evil-localhost:2999"}) {
            when(tenantService.isCorsAllowed(origin)).thenReturn(Mono.just(false));
            chainCalled.set(false);
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("http://gateway/api/v1/catalog/products").header(HttpHeaders.ORIGIN, origin));

            filter.filter(exchange, chain).block();

            assertFalse(chainCalled.get(), origin);
            assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode(), origin);
        }
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
