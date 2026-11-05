package com.kleinkauf.product.services;

import com.kleinkauf.common.dto.RoleDto;
import com.kleinkauf.common.dto.TenantDto;
import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.product.util.CacheUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/*
User and tenant data: first the local cache, then Redis and finally
the users microservice (UsersClient). Whatever could not be obtained is not cached.
 */
@Service
public class UserService {

    @Autowired
    private CacheUtil cache;

    @Autowired
    private UsersClient usersClient;

    @Autowired
    private LocalCacheService localCacheService;

    public List<RoleDto> getUserRolesById(Long userId){
        String tenant = TenantContext.getCurrentTenant();
        List<RoleDto> roles = localCacheService.getUserRoles(userId, tenant);
        if (roles != null) return roles;
        roles = cache.getUserRoles(userId, tenant);
        if (roles == null || roles.isEmpty()){
            roles = usersClient.getUserRoles(userId, tenant);
        }
        if (roles == null) return List.of();
        localCacheService.saveUserRoles(userId, tenant, roles);
        return roles;
    }

    public List<TenantDto> getTenants(){
        List<TenantDto> tenants = cache.getAllTenants();
        if (tenants != null && !tenants.isEmpty()) return tenants;
        return usersClient.getTenants();
    }

    public TenantDto getTenant(String schema){
        TenantDto tenant = localCacheService.getTenant(schema);
        if (tenant != null) return tenant;
        tenant = cache.getTenantBySchema(schema);
        if (tenant == null){
            tenant = usersClient.getTenant(schema);
        }
        if (tenant == null) return null;
        localCacheService.saveTenant(tenant);
        return tenant;
    }
}
