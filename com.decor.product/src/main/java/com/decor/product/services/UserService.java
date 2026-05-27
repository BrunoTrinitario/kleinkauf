package com.decor.product.services;

import com.decor.product.constants.Headers;
import com.decor.product.dto.RoleDto;
import com.decor.product.dto.TenantDto;
import com.decor.product.interceptor.TenantContext;
import com.decor.product.util.CacheUtil;
import com.decor.product.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserService {

    @Autowired
    private CacheUtil cache;

    @Value("${users.url}")
    private String usersUrl;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private LocalCacheService localCacheService;

    public List<RoleDto> getUserRolesById(Long userId){
        List<RoleDto> roles = localCacheService.getUserRoles(userId, TenantContext.getCurrentTenant());

        if (roles != null) return roles;

        roles = cache.getUserRoles(userId, TenantContext.getCurrentTenant());

        if (roles == null || roles.isEmpty()){
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-internal-request", Boolean.toString(true));
            headers.set("X-Internal-Token", jwtUtil.generateMicroServiceToken("products"));
            headers.set(Headers.FORWARDED_HOST, TenantContext.getCurrentTenant());
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<List<RoleDto>> response =
                    restTemplate.exchange(
                            usersUrl + "api/internal/users/roles/" + userId,
                            HttpMethod.GET,
                            entity,
                            new ParameterizedTypeReference<List<RoleDto>>() {}
                    );
            roles = response.getBody();
        }
        localCacheService.saveUserRoles(userId, TenantContext.getCurrentTenant(), roles);
        return roles;
    }

    public List<TenantDto> getTenants(){
        List<TenantDto> tenants = cache.getAllTenants();
        if  (tenants != null) return tenants;
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", jwtUtil.generateMicroServiceToken("products"));
        headers.set("X-internal-request", Boolean.toString(true));
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try{
            ResponseEntity<List<TenantDto>> response =
                    restTemplate.exchange(
                            usersUrl + "api/internal/tenants/all",
                            HttpMethod.GET,
                            entity,
                            new ParameterizedTypeReference<List<TenantDto>>() {}
                    );
            tenants = response.getBody();
        }catch (Exception e){
            return null;
        }
        return tenants;
    }

    public TenantDto getTenant(String schema){
        TenantDto tenant = localCacheService.getTenant(schema);

        if (tenant != null) return tenant;

        tenant = cache.getTenantBySchema(schema);

        if (tenant == null){
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Token", jwtUtil.generateMicroServiceToken("products"));
            headers.set("X-internal-request", Boolean.toString(true));
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<TenantDto> response =
                    restTemplate.exchange(
                            usersUrl + "api/internal/tenants/id?schema="+ schema,
                            HttpMethod.GET,
                            entity,
                            new ParameterizedTypeReference<TenantDto>() {}
                    );
            tenant = response.getBody();
        }
        localCacheService.saveTenant(tenant);
        return tenant ;
    }

}
