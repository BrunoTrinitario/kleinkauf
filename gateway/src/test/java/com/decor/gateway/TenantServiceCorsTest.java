package com.decor.gateway;

import com.decor.gateway.services.LocalCacheService;
import com.decor.gateway.services.TenantClient;
import com.decor.gateway.services.TenantService;
import com.decor.gateway.util.CacheUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceCorsTest {

    private static final String ORIGIN = "http://tienda1.com";

    @Mock
    private CacheUtil cacheUtil;

    @Mock
    private TenantClient tenantClient;

    @Mock
    private LocalCacheService localCacheService;

    @InjectMocks
    private TenantService tenantService;

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
