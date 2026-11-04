package com.kleinkauf.gateway.services;

import com.kleinkauf.common.dto.TenantDto;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class LocalCacheService {
    @Autowired
    @Qualifier("tenantCache")
    private Cache<String, Object> tenantCache;
    @Autowired
    private Cache<String, Integer> rateLimitCache;

    private Clock clock = Clock.systemUTC();

    // The tenant is looked up by the frontend URL (Origin), same as in users
    public void saveTenant(String url, TenantDto tenantDto) {
        tenantCache.put("tenant:url:"+url, tenantDto);
    }

    public TenantDto getTenant(String url) {
        return ((TenantDto) tenantCache.getIfPresent("tenant:url:"+url));
    }

    public void saveCorsTenant(String url){
        tenantCache.put("cors:"+url,Boolean.TRUE);
    }

    public boolean existsUrl(String url){
        return tenantCache.getIfPresent("cors:"+url) != null;
    }

    /*
    Counts the identifier's requests in the current minute's window and returns the total.
    Only if Redis does not answer (RateLimitCounter): the normal counter is in Redis, shared between gateways.
     */
    public int rateLimitCache(String requestIdentifier ){
        long window = clock.millis() / 60_000;
        return rateLimitCache.asMap().merge(requestIdentifier + ":" + window, 1, Integer::sum);
    }

    // For tests
    void setClock(Clock clock) {
        this.clock = clock;
    }

}
