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

    // url: el Origin de la request (cada tenant tiene su URL de frontend)
    public Mono<TenantDto> getTenant(String url) {

        TenantDto cached = localCacheService.getTenant(url);
        if (cached != null) {
            return Mono.just(cached);
        }

        // Si no esta en Redis (o Redis falla) se consulta a users
        return cacheUtil.getTenantByUrl(url)
                .onErrorResume(ex -> Mono.empty())
                .switchIfEmpty(Mono.defer(() -> tenantClient.getTenantByUrl(url)))
                .doOnNext(tenant -> localCacheService.saveTenant(url, tenant));
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
