package com.decor.product.services;

import com.decor.product.dto.RoleDto;
import com.decor.product.dto.TenantDto;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LocalCacheService {
    @Autowired
    private Cache<String, Object> cache;

    public void saveTenant(TenantDto tenantDto) {
        cache.put("tenant:"+tenantDto.schema(), tenantDto);
    }

    public TenantDto getTenant(String schema) {
        return ((TenantDto) cache.getIfPresent("tenant:"+schema));
    }

    public void saveUserRoles(long userId, String tenantIdentifier, List<RoleDto> roles) {
        String key = "user:"+userId+":"+tenantIdentifier;
        cache.put(key, roles);
    }

    public List<RoleDto> getUserRoles(long userId, String tenantIdentifier) {
        String key = "user:"+userId+":"+tenantIdentifier;
        return (List<RoleDto>) cache.getIfPresent(key);
    }

    public void invalidateUserRoles(long userId, String tenantIdentifier) {
        String key = "user:"+userId+":"+tenantIdentifier;
        cache.invalidate(key);
    }
}
