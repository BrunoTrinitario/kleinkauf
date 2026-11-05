package com.kleinkauf.gateway;

import com.kleinkauf.common.constants.Headers;
import com.kleinkauf.common.dto.TenantDto;
import com.kleinkauf.gateway.security.filters.TenantGatewayFilter;
import com.kleinkauf.gateway.services.TenantService;
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
    void enabledTenant_passesSchemaToMicroservice() {
        when(tenantService.getTenant(ORIGIN)).thenReturn(Mono.just(tenant(true)));
        MockServerWebExchange exchange = request("/api/v1/products/all");

        run(exchange);

        assertEquals("tienda_a", forwarded.get().getRequest().getHeaders().getFirst(Headers.FORWARDED_HOST));
    }

    @Test
    void unknownOrDeletedTenant_respondsNotFound() {
        when(tenantService.getTenant(ORIGIN)).thenReturn(Mono.empty());
        MockServerWebExchange exchange = request("/api/v1/products/all");

        run(exchange);

        assertNull(forwarded.get());
        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
        assertEquals("The tenant was not found.", body(exchange));
    }

    @Test
    void disabledTenant_respondsForbidden() {
        when(tenantService.getTenant(ORIGIN)).thenReturn(Mono.just(tenant(false)));
        MockServerWebExchange exchange = request("/api/v1/products/all");

        run(exchange);

        assertNull(forwarded.get());
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        assertEquals("The tenant is disabled.", body(exchange));
    }

    @Test
    void usersDown_respondsWithUsersError() {
        when(tenantService.getTenant(ORIGIN)).thenReturn(Mono.error(
                WebClientResponseException.create(503, "Service Unavailable", null, null, null)));
        MockServerWebExchange exchange = request("/api/v1/products/all");

        run(exchange);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
    }

    @Test
    void platformRoutes_doNotLookUpTenantOrAddHeader() {
        MockServerWebExchange exchange = request("/api/v1/platform/tenants");

        run(exchange);

        assertNotNull(forwarded.get());
        assertNull(forwarded.get().getRequest().getHeaders().getFirst(Headers.FORWARDED_HOST));
        verifyNoInteractions(tenantService);
    }

    // The browser does not send Origin when loading an <img>: images cannot depend on the tenant
    @Test
    void productImages_withoutOrigin_doNotLookUpTenant() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://gateway/products/images/abc_123.png"));

        run(exchange);

        assertNotNull(forwarded.get());
        assertNull(forwarded.get().getRequest().getHeaders().getFirst(Headers.FORWARDED_HOST));
        verifyNoInteractions(tenantService);
    }

    // Profile photos load from <img> too, without Origin
    @Test
    void userAvatars_withoutOrigin_doNotLookUpTenant() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://gateway/users/avatars/abc_123.png"));

        run(exchange);

        assertNotNull(forwarded.get());
        assertNull(forwarded.get().getRequest().getHeaders().getFirst(Headers.FORWARDED_HOST));
        verifyNoInteractions(tenantService);
    }

    @Test
    void serviceErrorsAreNotTurnedInto503() {
        when(tenantService.getTenant(ORIGIN)).thenReturn(Mono.just(tenant(true)));
        MockServerWebExchange exchange = request("/api/v1/products/all");
        IllegalStateException downstream = new IllegalStateException("products failure");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> filter.filter(exchange, e -> Mono.error(downstream)).block());

        assertEquals(downstream, thrown);
    }

    // WhatsApp's crawler does not send Origin: the store for the share link comes from the path, with platform.url
    @Test
    void shareLink_looksUpStoreByPath() {
        when(tenantService.getTenant("http://tienda-a.localhost:2999")).thenReturn(Mono.just(tenant(true)));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://gateway/share/tienda-a/products/mesa-de-roble"));

        run(exchange);

        assertEquals("tienda_a", forwarded.get().getRequest().getHeaders().getFirst(Headers.FORWARDED_HOST));
    }

    @Test
    void shareLinkOfDisabledStore_respondsForbidden() {
        when(tenantService.getTenant("http://tienda-a.localhost:2999")).thenReturn(Mono.just(tenant(false)));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://gateway/share/tienda-a/products/mesa-de-roble"));

        run(exchange);

        assertNull(forwarded.get());
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    // The superadmin dashboard opens the WS from the platform frontend: there is no store to look up
    @Test
    void wsFromPlatform_doesNotLookUpTenant() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://gateway/ws?ticket=abc").header(HttpHeaders.ORIGIN, PLATFORM_ORIGIN));

        run(exchange);

        assertNotNull(forwarded.get());
        assertNull(forwarded.get().getRequest().getHeaders().getFirst(Headers.FORWARDED_HOST));
        verifyNoInteractions(tenantService);
    }

    // From a store it is resolved like any request: observability compares the tenant with the ticket's
    @Test
    void wsFromStore_passesTheSchema() {
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
