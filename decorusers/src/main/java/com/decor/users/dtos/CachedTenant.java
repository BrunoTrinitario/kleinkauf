package com.decor.users.dtos;

import com.decor.users.constants.CacheKeys;
import lombok.Getter;

@Getter
public class CachedTenant {
    private String key;
    private String hashKey = "data";
    private TenantDto tenantData;
    private long duration = 1;

    public CachedTenant(String tenantSchema, TenantDto tenantData) {
        this.key = CacheKeys.CACHE_TENANT + tenantSchema;
        this.tenantData = tenantData;
    }

    public CachedTenant(String key, String url, long duration) {
        this.key = key + url;
        this.tenantData = null;
        this.duration = duration;
    }


}
