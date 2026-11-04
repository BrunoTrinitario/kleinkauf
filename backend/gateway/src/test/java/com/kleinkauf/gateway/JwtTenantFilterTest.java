package com.kleinkauf.gateway;

import com.kleinkauf.common.constants.Headers;
import com.kleinkauf.common.constants.TokenConstants;
import com.kleinkauf.gateway.security.filters.JwtFilter;
import com.kleinkauf.gateway.util.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// El token tiene que ser del tenant de la request (los ids de usuario se repiten entre tenants)
class JwtTenantFilterTest {

    private static final String SECRET = "MiSuperSecretoUltraSeguro123456789";

    private final JwtFilter filter;
    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

    JwtTenantFilterTest() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        jwtUtil.init();
        filter = new JwtFilter(jwtUtil);
    }

    @Test
    void tokenDelMismoTenant_pasaConElUsuario() {
        MockServerWebExchange exchange = tenantRequest("/api/v1/products/5/pricing", "tienda_a", token(7L, "tienda_a"));

        run(exchange);

        assertEquals("7", forwarded.get().getRequest().getHeaders().getFirst(Headers.USER_ID));
    }

    @Test
    void tokenDeOtroTenant_respondeUnauthorized() {
        MockServerWebExchange exchange = tenantRequest("/api/v1/products/5/pricing", "tienda_b", token(7L, "tienda_a"));

        run(exchange);

        assertNull(forwarded.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    // El token del mail de recuperacion de contrasena no sirve para entrar
    @Test
    void tokenDeRecuperacion_respondeUnauthorized() {
        MockServerWebExchange exchange = tenantRequest("/api/v1/products/5/pricing", "tienda_a",
                token(7L, "tienda_a", TokenConstants.PASSWORD_RESET));

        run(exchange);

        assertNull(forwarded.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());

        run(tenantRequest("/api/v1/products/5/pricing", "tienda_a", token(7L, "tienda_a", TokenConstants.ACCESS)));
        assertEquals("7", forwarded.get().getRequest().getHeaders().getFirst(Headers.USER_ID));
    }

    // El refresh solo sirve para POST /auth/refresh, no para entrar
    @Test
    void tokenDeRefresh_respondeUnauthorized() {
        MockServerWebExchange exchange = tenantRequest("/api/v1/products/5/pricing", "tienda_a",
                token(7L, "tienda_a", TokenConstants.REFRESH));

        run(exchange);

        assertNull(forwarded.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void tokenViejoSinTenant_respondeUnauthorized() {
        MockServerWebExchange exchange = tenantRequest("/api/v1/products/5/pricing", "tienda_a", token(7L, null));

        run(exchange);

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void plataforma_soloAceptaTokensDePublic() {
        MockServerWebExchange deTienda = platformRequest(token(1L, "tienda_a"));
        run(deTienda);
        assertEquals(HttpStatus.UNAUTHORIZED, deTienda.getResponse().getStatusCode());

        MockServerWebExchange dePlataforma = platformRequest(token(1L, "public"));
        run(dePlataforma);
        assertEquals("1", forwarded.get().getRequest().getHeaders().getFirst(Headers.USER_ID));
    }

    @Test
    void crearPedido_loginOpcional() {
        // Invitado: pasa sin usuario
        MockServerWebExchange invitado = MockServerWebExchange.from(MockServerHttpRequest.post("http://gateway/api/v1/sales/orders")
                .header(Headers.FORWARDED_HOST, "tienda_a"));
        run(invitado);
        assertNull(forwarded.get().getRequest().getHeaders().getFirst(Headers.USER_ID));

        // Logueado: pasa con el usuario para asociarle el pedido
        run(orderRequest("tienda_a", token(7L, "tienda_a")));
        assertEquals("7", forwarded.get().getRequest().getHeaders().getFirst(Headers.USER_ID));

        // Token invalido o de otra tienda: 401 (no se crea como invitado sin avisar)
        MockServerWebExchange deOtraTienda = orderRequest("tienda_b", token(7L, "tienda_a"));
        run(deOtraTienda);
        assertNull(forwarded.get());
        assertEquals(HttpStatus.UNAUTHORIZED, deOtraTienda.getResponse().getStatusCode());
    }

    @Test
    void consultaDePedido_ignoraElToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("http://gateway/api/v1/sales/orders/lookup")
                .header(Headers.FORWARDED_HOST, "tienda_a")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(7L, "tienda_a")));
        run(exchange);
        assertNull(forwarded.get().getRequest().getHeaders().getFirst(Headers.USER_ID));
    }

    private static MockServerWebExchange orderRequest(String schema, String token) {
        return MockServerWebExchange.from(MockServerHttpRequest.post("http://gateway/api/v1/sales/orders")
                .header(Headers.FORWARDED_HOST, schema)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private void run(MockServerWebExchange exchange) {
        forwarded.set(null);
        filter.filter(exchange, e -> {
            forwarded.set(e);
            return Mono.empty();
        }).block();
    }

    // Simula lo que deja TenantGatewayFilter (corre antes): el schema en X-Forwarded-Host
    private static MockServerWebExchange tenantRequest(String path, String schema, String token) {
        return MockServerWebExchange.from(MockServerHttpRequest.get("http://gateway" + path)
                .header(Headers.FORWARDED_HOST, schema)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static MockServerWebExchange platformRequest(String token) {
        return MockServerWebExchange.from(MockServerHttpRequest.get("http://gateway/api/v1/platform/admin/tenants")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static String token(Long userId, String tenant) {
        return token(userId, tenant, null);
    }

    private static String token(Long userId, String tenant, String type) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", userId);
        if (tenant != null) claims.put("tenant", tenant);
        if (type != null) claims.put(TokenConstants.TYPE_CLAIM, type);
        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()))
                .compact();
    }
}
