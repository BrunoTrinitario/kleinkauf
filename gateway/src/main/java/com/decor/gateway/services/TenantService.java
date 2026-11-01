package com.decor.gateway.services;

import com.decor.common.dto.TenantDto;
import com.decor.gateway.util.CacheUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;


@Service
public class TenantService {

    @Autowired
    private CacheUtil cacheUtil;

    @Autowired
    private TenantClient tenantClient;

    @Autowired
    public LocalCacheService localCacheService;

    public Mono<TenantDto> getTenant(String schema) {

        TenantDto cached = localCacheService.getTenant(schema);
        if (cached != null) {
            return Mono.just(cached);
        }

        cached = cacheUtil.getTenantBySchema(schema);
        if (cached != null) {
            return Mono.just(cached);
        }

        return tenantClient.getTenantByUrl(schema);
    }

    /*
    Indica si el origen pertenece a un tenant: cache local, despues Redis y por ultimo users.
    Todo reactivo para no bloquear el event loop. Solo se cachean los origenes permitidos.
     */
    public Mono<Boolean> isCorsAllowed(String url) {
        if (localCacheService.existsUrl(url)) {
            return Mono.just(true);
        }

        // Si Redis falla se consulta a users directamente
        return cacheUtil.existsTenantByUrl(url)
                .onErrorReturn(false)
                .flatMap(cached -> cached ? Mono.just(true) : tenantClient.isCorsAllowed(url))
                .map(Boolean.TRUE::equals)
                .onErrorReturn(false)
                .defaultIfEmpty(false)
                .doOnNext(allowed -> {
                    if (allowed) localCacheService.saveCorsTenant(url);
                });
    }
}
