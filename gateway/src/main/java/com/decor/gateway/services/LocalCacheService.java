package com.decor.gateway.services;

import com.decor.common.dto.TenantDto;
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

    // El tenant se busca por la URL del frontend (Origin), igual que en users
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
    Cuenta las requests del identificador en la ventana del minuto actual y devuelve el total.
    Antes nunca incrementaba (siempre devolvia 1), asi que el rate limit no funcionaba.
     */
    public int rateLimitCache(String requestIdentifier ){
        long window = clock.millis() / 60_000;
        return rateLimitCache.asMap().merge(requestIdentifier + ":" + window, 1, Integer::sum);
    }

    // Para tests
    void setClock(Clock clock) {
        this.clock = clock;
    }

}
