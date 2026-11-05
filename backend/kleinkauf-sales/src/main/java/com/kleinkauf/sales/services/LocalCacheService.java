package com.kleinkauf.sales.services;

import com.kleinkauf.common.dto.RoleDto;
import com.kleinkauf.common.dto.TenantDto;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LocalCacheService {
    @Autowired
    @Qualifier("caffeineCache")
    private Cache<String, Object> cache;

    // Roles with their permissions: 1 minute (see CaffeineConfig)
    @Autowired
    @Qualifier("userRolesCache")
    private Cache<String, Object> userRolesCache;

    public void saveTenant(TenantDto tenantDto) {
        cache.put("tenant:"+tenantDto.schema(), tenantDto);
    }

    public TenantDto getTenant(String schema) {
        return ((TenantDto) cache.getIfPresent("tenant:"+schema));
    }

    public void saveUserRoles(long userId, String tenantIdentifier, List<RoleDto> roles) {
        String key = "user:"+userId+":"+tenantIdentifier;
        userRolesCache.put(key, roles);
    }

    @SuppressWarnings("unchecked")
    public List<RoleDto> getUserRoles(long userId, String tenantIdentifier) {
        String key = "user:"+userId+":"+tenantIdentifier;
        return (List<RoleDto>) userRolesCache.getIfPresent(key);
    }

    public void invalidateUserRoles(long userId, String tenantIdentifier) {
        String key = "user:"+userId+":"+tenantIdentifier;
        userRolesCache.invalidate(key);
    }
}
