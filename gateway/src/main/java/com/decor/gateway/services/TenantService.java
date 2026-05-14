package com.decor.gateway.services;

import com.decor.gateway.dtos.TenantDto;
import com.decor.gateway.util.CacheUtil;
import com.decor.gateway.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class TenantService {

    @Autowired
    private CacheUtil cacheUtil;

    @Value("${tenant.url}")
    private String tenantUrl;

    @Autowired
    JwtUtil jwtUtil;

    private final WebClient webClient = WebClient.create();

    public Mono<TenantDto> getTenant(String url) {

        //TenantDto cached = cacheUtil.getFullTenant(url);
//
        //if (cached != null) {
        //    return Mono.just(cached);
        //}

        return webClient.get()
                .uri(tenantUrl + "/id?name=" + url)
                .header("X-Internal-Request", "true")
                .header("X-Internal-Token",jwtUtil.generateMicroServiceToken("gateway"))
                .retrieve()
                .bodyToMono(TenantDto.class);
    }
}
