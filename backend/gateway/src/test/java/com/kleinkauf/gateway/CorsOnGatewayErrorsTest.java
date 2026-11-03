package com.kleinkauf.gateway;

import com.kleinkauf.gateway.config.CorsConfig;
import com.kleinkauf.gateway.security.filters.ClientHeadersFilter;
import com.kleinkauf.gateway.security.filters.JwtFilter;
import com.kleinkauf.gateway.security.filters.RequestFilter;
import com.kleinkauf.gateway.security.filters.TenantGatewayFilter;
import com.kleinkauf.gateway.services.TenantService;
import com.kleinkauf.gateway.util.JwtUtil;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.handler.DefaultWebFilterChain;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Las respuestas que corta el gateway (401, 404/403 de la tienda, 429) tienen que salir con los headers CORS:
// sin ellos el navegador las ve como un error de red y el frontend no puede leer el codigo
class CorsOnGatewayErrorsTest {

    private static final String PLATFORM_URL = "http://localhost:2999";
    private static final String ORIGIN = "http://tienda-a.localhost:2999";

    private final TenantService tenantService = mock(TenantService.class);

    @Test
    void corsCorreDespuesDeLimpiarHeadersYAntesDeLosFiltrosQueCortan() {
        int cors = order(CorsConfig.class);

        assertTrue(order(ClientHeadersFilter.class) < cors);
        assertTrue(cors < order(RequestFilter.class), "rate limit (429)");
        assertTrue(cors < order(TenantGatewayFilter.class), "tenant (404/403)");
        assertTrue(cors < order(JwtFilter.class), "JWT (401)");
    }

    @Test
    void sinToken_elUnauthorizedSaleConHeadersCors() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "MiSuperSecretoUltraSeguro123456789");
        jwtUtil.init();
        MockServerWebExchange exchange = request("/api/v1/products/5/pricing");

        run(exchange, cors(), new JwtFilter(jwtUtil));

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertEquals(ORIGIN, exchange.getResponse().getHeaders().getAccessControlAllowOrigin());
    }

    @Test
    void tiendaInexistente_elNotFoundSaleConHeadersCors() {
        when(tenantService.getTenant(ORIGIN)).thenReturn(Mono.empty());
        TenantGatewayFilter tenantFilter = new TenantGatewayFilter();
        ReflectionTestUtils.setField(tenantFilter, "tenantService", tenantService);
        ReflectionTestUtils.setField(tenantFilter, "platformUrl", PLATFORM_URL);
        MockServerWebExchange exchange = request("/api/v1/catalog/products");

        run(exchange, cors(), tenantFilter);

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
        assertEquals(ORIGIN, exchange.getResponse().getHeaders().getAccessControlAllowOrigin());
    }

    private CorsConfig cors() {
        CorsConfig cors = new CorsConfig(tenantService);
        ReflectionTestUtils.setField(cors, "platformUrl", PLATFORM_URL);
        return cors;
    }

    private static MockServerWebExchange request(String path) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("http://gateway" + path).header(HttpHeaders.ORIGIN, ORIGIN));
    }

    private static void run(MockServerWebExchange exchange, WebFilter... filters) {
        new DefaultWebFilterChain(ex -> Mono.empty(), List.of(filters)).filter(exchange).block();
    }

    private static int order(Class<?> type) {
        return AnnotationUtils.findAnnotation(type, Order.class).value();
    }
}
