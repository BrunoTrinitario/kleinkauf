package com.kleinkauf.gateway;

import com.kleinkauf.common.constants.Headers;
import com.kleinkauf.gateway.security.filters.ClientHeadersFilter;
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
}
