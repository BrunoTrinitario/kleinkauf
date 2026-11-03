package com.kleinkauf.gateway;

import com.kleinkauf.common.dto.TenantDto;
import com.kleinkauf.gateway.services.LocalCacheService;
import com.kleinkauf.gateway.services.TenantClient;
import com.kleinkauf.gateway.services.TenantService;
import com.kleinkauf.gateway.util.CacheUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    private static final String ORIGIN = "http://tienda1.com";

    @Mock
    private CacheUtil cacheUtil;

    @Mock
    private TenantClient tenantClient;

    @Mock
    private LocalCacheService localCacheService;

    @InjectMocks
    private TenantService tenantService;

    private final TenantDto tenant = new TenantDto(1L, "cfg", "tienda1", true, null, null);

    // getTenant

    @Test
    void getTenant_enCacheLocal_noConsultaRedisNiUsers() {
        when(localCacheService.getTenant(ORIGIN)).thenReturn(tenant);

        assertEquals(tenant, tenantService.getTenant(ORIGIN).block());
        verifyNoInteractions(cacheUtil, tenantClient);
    }

    @Test
    void getTenant_enRedis_noConsultaUsers() {
        when(cacheUtil.getTenantByUrl(ORIGIN)).thenReturn(Mono.just(tenant));

        assertEquals(tenant, tenantService.getTenant(ORIGIN).block());
        verifyNoInteractions(tenantClient);
    }

    @Test
    void getTenant_noEstaEnRedis_consultaAUsers() {
        when(cacheUtil.getTenantByUrl(ORIGIN)).thenReturn(Mono.empty());
        when(tenantClient.getTenantByUrl(ORIGIN)).thenReturn(Mono.just(tenant));

        assertEquals(tenant, tenantService.getTenant(ORIGIN).block());
        // Queda en el cache local por URL (TTL de 1 minuto)
        verify(localCacheService).saveTenant(ORIGIN, tenant);
    }

    @Test
    void getTenant_redisCaido_consultaAUsers() {
        when(cacheUtil.getTenantByUrl(ORIGIN)).thenReturn(Mono.error(new RuntimeException("redis down")));
        when(tenantClient.getTenantByUrl(ORIGIN)).thenReturn(Mono.just(tenant));

        assertEquals(tenant, tenantService.getTenant(ORIGIN).block());
    }

    // isCorsAllowed

    @Test
    void enCacheLocal_noConsultaRedisNiUsers() {
        when(localCacheService.existsUrl(ORIGIN)).thenReturn(true);

        assertTrue(tenantService.isCorsAllowed(ORIGIN).block());
        verifyNoInteractions(cacheUtil, tenantClient);
    }

    @Test
    void enRedis_permiteYCacheaLocal() {
        when(cacheUtil.existsTenantByUrl(ORIGIN)).thenReturn(Mono.just(true));

        assertTrue(tenantService.isCorsAllowed(ORIGIN).block());
        verify(localCacheService).saveCorsTenant(ORIGIN);
        verifyNoInteractions(tenantClient);
    }

    @Test
    void usersLoPermite_permiteYCacheaLocal() {
        when(cacheUtil.existsTenantByUrl(ORIGIN)).thenReturn(Mono.just(false));
        when(tenantClient.isCorsAllowed(ORIGIN)).thenReturn(Mono.just(true));

        assertTrue(tenantService.isCorsAllowed(ORIGIN).block());
        verify(localCacheService).saveCorsTenant(ORIGIN);
    }

    @Test
    void origenDesconocido_noSeCachea() {
        when(cacheUtil.existsTenantByUrl(ORIGIN)).thenReturn(Mono.just(false));
        when(tenantClient.isCorsAllowed(ORIGIN)).thenReturn(Mono.just(false));

        assertFalse(tenantService.isCorsAllowed(ORIGIN).block());
        verify(localCacheService, never()).saveCorsTenant(anyString());
    }

    @Test
    void redisCaido_consultaAUsers() {
        when(cacheUtil.existsTenantByUrl(ORIGIN)).thenReturn(Mono.error(new RuntimeException("redis down")));
        when(tenantClient.isCorsAllowed(ORIGIN)).thenReturn(Mono.just(true));

        assertTrue(tenantService.isCorsAllowed(ORIGIN).block());
    }

    @Test
    void usersFalla_noPermite() {
        when(cacheUtil.existsTenantByUrl(ORIGIN)).thenReturn(Mono.just(false));
        when(tenantClient.isCorsAllowed(ORIGIN)).thenReturn(Mono.error(new RuntimeException("users down")));

        assertFalse(tenantService.isCorsAllowed(ORIGIN).block());
        verify(localCacheService, never()).saveCorsTenant(anyString());
    }
}
