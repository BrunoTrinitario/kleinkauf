package com.decor.gateway.services;

import com.decor.gateway.dtos.TenantDto;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LocalCacheService {
    @Autowired
    private Cache<String, Object> cache;
    @Autowired
    private Cache<String, Integer> rateLimitCache;

    public void saveTenant(TenantDto tenantDto) {
        cache.put("tenant:"+tenantDto.schema(), tenantDto);
    }

    public TenantDto getTenant(String schema) {
        return ((TenantDto) cache.getIfPresent("tenant:"+schema));
    }

    public void saveCorsTenant(String url){
        cache.put("cors:"+url,Boolean.TRUE);
    }

    public boolean existsUrl(String url){
        return cache.getIfPresent("cors:"+url) != null;
    }

    public int rateLimitCache(String requestIdentifier ){
        Integer cont = rateLimitCache.getIfPresent(requestIdentifier);
        if (cont == null){
            cont = 1;
            rateLimitCache.put(requestIdentifier, cont);
        }
        return cont;
    }

}
