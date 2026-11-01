package com.decor.product.services;

import com.decor.common.dto.RoleDto;
import com.decor.common.dto.TenantDto;
import com.decor.common.servlet.tenant.TenantContext;
import com.decor.product.util.CacheUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/*
Datos de usuarios y tenants: primero cache local, despues Redis y por ultimo
el microservicio de usuarios (UsersClient). Lo que no se pudo obtener no se cachea.
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
