package com.kleinkauf.gateway;

import com.kleinkauf.common.constants.Headers;
import com.kleinkauf.gateway.factories.RequestTenantFactory;
import com.kleinkauf.gateway.security.filters.ClientHeadersFilter;
import com.kleinkauf.gateway.util.Parser;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ClientHeadersFilterTest {

    @Test
    void borraLosHeadersInternosQueMandaElCliente() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("http://gateway/api/v1/platform/admin/tenants")
                .header(Headers.USER_ID, "1")
                .header(Headers.FORWARDED_HOST, "tienda_a")
                .header(Headers.OPEN_PATH, "true")
                .header(Headers.TENANT_NAME, "tienda_a")
                .header(Headers.CLIENT_IP, "1.1.1.1")
                .remoteAddress(new InetSocketAddress("190.1.2.3", 51000))
                .header(HttpHeaders.AUTHORIZATION, "Bearer x")
                .header(HttpHeaders.ORIGIN, "http://tienda-a.localhost:3000"));
        AtomicReference<HttpHeaders> received = new AtomicReference<>();

        new ClientHeadersFilter().filter(exchange, e -> {
            received.set(e.getRequest().getHeaders());
            return Mono.empty();
        }).block();

        HttpHeaders headers = received.get();
        assertFalse(headers.containsKey(Headers.USER_ID));
        assertFalse(headers.containsKey(Headers.FORWARDED_HOST));
        assertFalse(headers.containsKey(Headers.OPEN_PATH));
        assertFalse(headers.containsKey(Headers.TENANT_NAME));
        // La IP para la auditoria es la de la conexion, no la que manda el cliente
        assertEquals("190.1.2.3", headers.getFirst(Headers.CLIENT_IP));
        // Los demas siguen
        assertEquals("Bearer x", headers.getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("http://tienda-a.localhost:3000", headers.getOrigin());
    }

    // Detras de Caddy (forward-headers-strategy=framework) Spring arma la direccion desde X-Forwarded-For sin
    // resolverla: getAddress() es null
    @Test
    void conLaDireccionSinResolverUsaLaIpIgual() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("http://gateway/api/v1/platform/auth/login")
                .remoteAddress(InetSocketAddress.createUnresolved("190.1.2.3", 0))
                .header(HttpHeaders.ORIGIN, "https://plataforma.example"));
        AtomicReference<HttpHeaders> received = new AtomicReference<>();

        new ClientHeadersFilter().filter(exchange, e -> {
            received.set(e.getRequest().getHeaders());
            return Mono.empty();
        }).block();

        assertEquals("190.1.2.3", received.get().getFirst(Headers.CLIENT_IP));
        assertEquals("rate_limit:" + RequestTenantFactory.generatePathType("/api/v1/platform/auth/login")
                + ":https://plataforma.example_190.1.2.3", Parser.generateRequestIdentifier(exchange));
    }
}
