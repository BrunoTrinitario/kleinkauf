package com.decor.gateway;

import com.decor.common.constants.Headers;
import com.decor.common.dto.TenantDto;
import com.decor.gateway.security.filters.TenantGatewayFilter;
import com.decor.gateway.services.TenantService;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TenantGatewayFilterTest {

    private static final String ORIGIN = "http://tienda-a.localhost:3000";
    private static final String PLATFORM_ORIGIN = "http://localhost:2999";

    private final TenantService tenantService = mock(TenantService.class);
    private final TenantGatewayFilter filter = new TenantGatewayFilter();
    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

    TenantGatewayFilterTest() {
        ReflectionTestUtils.setField(filter, "tenantService", tenantService);
        ReflectionTestUtils.setField(filter, "platformUrl", PLATFORM_ORIGIN);
    }

    @Test
    void tenantHabilitado_pasaElSchemaAlMicroservicio() {
        when(tenantService.getTenant(ORIGIN)).thenReturn(Mono.just(tenant(true)));
        MockServerWebExchange exchange = request("/api/v1/products/all");

        run(exchange);

        assertEquals("tienda_a", forwarded.get().getRequest().getHeaders().getFirst(Headers.FORWARDED_HOST));
    }

    @Test
    void tenantInexistenteOBorrado_respondeNotFound() {
        when(tenantService.getTenant(ORIGIN)).thenReturn(Mono.empty());
        MockServerWebExchange exchange = request("/api/v1/products/all");

        run(exchange);

        assertNull(forwarded.get());
        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
        assertEquals("The tenant was not found.", body(exchange));
    }

    @Test
    void tenantDeshabilitado_respondeForbidden() {
        when(tenantService.getTenant(ORIGIN)).thenReturn(Mono.just(tenant(false)));
        MockServerWebExchange exchange = request("/api/v1/products/all");

        run(exchange);

        assertNull(forwarded.get());
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        assertEquals("The tenant is disabled.", body(exchange));
    }

    @Test
    void usersCaido_respondeElErrorDeUsers() {
        when(tenantService.getTenant(ORIGIN)).thenReturn(Mono.error(
                WebClientResponseException.create(503, "Service Unavailable", null, null, null)));
        MockServerWebExchange exchange = request("/api/v1/products/all");

        run(exchange);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
    }

    @Test
    void rutasDePlataforma_noBuscanTenantNiAgreganElHeader() {
        MockServerWebExchange exchange = request("/api/v1/platform/tenants");

        run(exchange);

        assertNotNull(forwarded.get());
        assertNull(forwarded.get().getRequest().getHeaders().getFirst(Headers.FORWARDED_HOST));
        verifyNoInteractions(tenantService);
    }

    // El navegador no manda Origin al cargar un <img>: las imagenes no pueden depender del tenant
    @Test
    void imagenesDeProductos_sinOrigin_noBuscanTenant() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://gateway/products/images/abc_123.png"));

        run(exchange);

        assertNotNull(forwarded.get());
        assertNull(forwarded.get().getRequest().getHeaders().getFirst(Headers.FORWARDED_HOST));
        verifyNoInteractions(tenantService);
    }

    @Test
    void losErroresDeLosServiciosNoSeConviertenEn503() {
        when(tenantService.getTenant(ORIGIN)).thenReturn(Mono.just(tenant(true)));
        MockServerWebExchange exchange = request("/api/v1/products/all");
        IllegalStateException downstream = new IllegalStateException("fallo en products");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> filter.filter(exchange, e -> Mono.error(downstream)).block());

        assertEquals(downstream, thrown);
    }

    // El panel del superadmin abre el WS desde el frontend de la plataforma: no hay tienda que buscar
    @Test
    void wsDesdeLaPlataforma_noBuscaTenant() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://gateway/ws?ticket=abc").header(HttpHeaders.ORIGIN, PLATFORM_ORIGIN));

        run(exchange);

        assertNotNull(forwarded.get());
        assertNull(forwarded.get().getRequest().getHeaders().getFirst(Headers.FORWARDED_HOST));
        verifyNoInteractions(tenantService);
    }

    // Desde una tienda se resuelve como cualquier request: observability compara el tenant con el del ticket
    @Test
    void wsDesdeUnaTienda_pasaElSchema() {
        when(tenantService.getTenant(ORIGIN)).thenReturn(Mono.just(tenant(true)));
        MockServerWebExchange exchange = request("/ws");

        run(exchange);

        assertEquals("tienda_a", forwarded.get().getRequest().getHeaders().getFirst(Headers.FORWARDED_HOST));
    }

    private void run(MockServerWebExchange exchange) {
        filter.filter(exchange, e -> {
            forwarded.set(e);
            return Mono.empty();
        }).block();
    }

    private static MockServerWebExchange request(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get("http://gateway" + path).header(HttpHeaders.ORIGIN, ORIGIN));
    }

    private static TenantDto tenant(boolean enabled) {
        return new TenantDto(1L, "cfg", "tienda_a", enabled, null, null);
    }

    private static String body(MockServerWebExchange exchange) {
        return exchange.getResponse().getBodyAsString().block();
    }
}
