package com.kleinkauf.gateway.services;

import com.kleinkauf.common.dto.TenantDto;
import com.kleinkauf.gateway.util.CacheUtil;
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

    // url: the request's Origin (each tenant has its own frontend URL)
    public Mono<TenantDto> getTenant(String url) {

        TenantDto cached = localCacheService.getTenant(url);
        if (cached != null) {
            return Mono.just(cached);
        }

        // If it is not in Redis (or Redis fails) users is queried
        return cacheUtil.getTenantByUrl(url)
                .onErrorResume(ex -> Mono.empty())
                .switchIfEmpty(Mono.defer(() -> tenantClient.getTenantByUrl(url)))
                .doOnNext(tenant -> localCacheService.saveTenant(url, tenant));
    }

    /*
    Tells whether the origin belongs to a tenant: local cache, then Redis and finally users.
    All reactive so as not to block the event loop. Only allowed origins are cached.
     */
    public Mono<Boolean> isCorsAllowed(String url) {
        if (localCacheService.existsUrl(url)) {
            return Mono.just(true);
        }

        // If Redis fails users is queried directly
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
