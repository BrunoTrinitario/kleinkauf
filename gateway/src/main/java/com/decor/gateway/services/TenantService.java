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

    public boolean getTenantForCorsSync(String url) {
        boolean cached = false;

        cached = localCacheService.existsUrl(url);
        if (cached) return true;

        cached = cacheUtil.existsTenantByUrl(url);
        if (cached) return true;

        Boolean allowed = tenantClient.isCorsAllowed(url)
                .onErrorReturn(false)
                .block();

        // Solo se cachean los origenes permitidos
        if (Boolean.TRUE.equals(allowed)) {
            localCacheService.saveCorsTenant(url);
        }

        return Boolean.TRUE.equals(allowed);
    }
}
