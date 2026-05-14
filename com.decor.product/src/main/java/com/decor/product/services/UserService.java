package com.decor.product.services;

import com.decor.product.CacheUtil;
import com.decor.product.dto.RoleDto;
import com.decor.product.dto.TenantDto;
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


    private final RestTemplate restTemplate = new RestTemplate();

    public List<RoleDto> getUserRolesById(Long userId){
        List<RoleDto> roles = cache.getUserRoles(userId);
        if (roles.isEmpty()){
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-internal-request", Boolean.toString(true));
            headers.set("X-Internal-Token", jwtUtil.generateMicroServiceToken("products"));

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
        return roles;
    }

    public List<TenantDto> getTenants(){
        List<TenantDto> tenants = new ArrayList<>();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", jwtUtil.generateMicroServiceToken("products"));
        headers.set("X-internal-request", Boolean.toString(true));
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<List<TenantDto>> response =
                restTemplate.exchange(
                        usersUrl + "api/internal/tenants/all",
                        HttpMethod.GET,
                        entity,
                        new ParameterizedTypeReference<List<TenantDto>>() {}
                );
        tenants = response.getBody();
        return tenants;
    }

    public TenantDto getTenant(String schema){
        TenantDto tenant = null;

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
        return tenant ;
    }

}
