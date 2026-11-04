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

// The token must belong to the request's tenant (user ids repeat across tenants)
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
    void tokenFromSameTenant_passesWithTheUser() {
        MockServerWebExchange exchange = tenantRequest("/api/v1/products/5/pricing", "tienda_a", token(7L, "tienda_a"));

        run(exchange);

        assertEquals("7", forwarded.get().getRequest().getHeaders().getFirst(Headers.USER_ID));
    }

    @Test
    void tokenFromAnotherTenant_respondsUnauthorized() {
        MockServerWebExchange exchange = tenantRequest("/api/v1/products/5/pricing", "tienda_b", token(7L, "tienda_a"));

        run(exchange);

        assertNull(forwarded.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    // The password recovery email token does not work to log in
    @Test
    void recoveryToken_respondsUnauthorized() {
        MockServerWebExchange exchange = tenantRequest("/api/v1/products/5/pricing", "tienda_a",
                token(7L, "tienda_a", TokenConstants.PASSWORD_RESET));

        run(exchange);

        assertNull(forwarded.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());

        run(tenantRequest("/api/v1/products/5/pricing", "tienda_a", token(7L, "tienda_a", TokenConstants.ACCESS)));
        assertEquals("7", forwarded.get().getRequest().getHeaders().getFirst(Headers.USER_ID));
    }

    // The refresh token only works for POST /auth/refresh, not to log in
    @Test
    void refreshToken_respondsUnauthorized() {
        MockServerWebExchange exchange = tenantRequest("/api/v1/products/5/pricing", "tienda_a",
                token(7L, "tienda_a", TokenConstants.REFRESH));

        run(exchange);

        assertNull(forwarded.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void oldTokenWithoutTenant_respondsUnauthorized() {
        MockServerWebExchange exchange = tenantRequest("/api/v1/products/5/pricing", "tienda_a", token(7L, null));

        run(exchange);

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void platform_onlyAcceptsPublicTokens() {
        MockServerWebExchange fromStore = platformRequest(token(1L, "tienda_a"));
        run(fromStore);
        assertEquals(HttpStatus.UNAUTHORIZED, fromStore.getResponse().getStatusCode());

        MockServerWebExchange fromPlatform = platformRequest(token(1L, "public"));
        run(fromPlatform);
        assertEquals("1", forwarded.get().getRequest().getHeaders().getFirst(Headers.USER_ID));
    }

    @Test
    void createOrder_optionalLogin() {
        // Guest: passes without a user
        MockServerWebExchange guest = MockServerWebExchange.from(MockServerHttpRequest.post("http://gateway/api/v1/sales/orders")
                .header(Headers.FORWARDED_HOST, "tienda_a"));
        run(guest);
        assertNull(forwarded.get().getRequest().getHeaders().getFirst(Headers.USER_ID));

        // Logged in: passes with the user so the order is linked to them
        run(orderRequest("tienda_a", token(7L, "tienda_a")));
        assertEquals("7", forwarded.get().getRequest().getHeaders().getFirst(Headers.USER_ID));

        // Invalid token or one from another store: 401 (it is not silently created as a guest)
        MockServerWebExchange fromAnotherStore = orderRequest("tienda_b", token(7L, "tienda_a"));
        run(fromAnotherStore);
        assertNull(forwarded.get());
        assertEquals(HttpStatus.UNAUTHORIZED, fromAnotherStore.getResponse().getStatusCode());
    }

    @Test
    void orderLookup_ignoresTheToken() {
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

    // Simulates what TenantGatewayFilter leaves (runs before): the schema in X-Forwarded-Host
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
