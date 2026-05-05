package com.decor.product.services;

import com.decor.product.CacheUtil;
import com.decor.product.dto.RoleDto;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class UserService {

    @Autowired
    private CacheUtil cache;

    @Value("${gateway.url}")
    private String gatewayUrl;

    @Value("${api.internal.key}")
    private String apiInternalKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public List<RoleDto> getUserRolesById(Long userId){
        List<RoleDto> roles = cache.getUserRoles(userId);
        if (roles.isEmpty()){
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Key", apiInternalKey);

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<List<RoleDto>> response =
                    restTemplate.exchange(
                            gatewayUrl + "api/internal/users/roles/" + userId,
                            HttpMethod.GET,
                            entity,
                            new ParameterizedTypeReference<List<RoleDto>>() {}
                    );
            roles = response.getBody();
        }
        return roles;
    }
}
