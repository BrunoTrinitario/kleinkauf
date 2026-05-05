package com.decor.gateway.services;

import com.decor.gateway.dtos.ComTenantDto;
import com.decor.gateway.util.CacheUtil;
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

    private final WebClient webClient = WebClient.create();

    public Mono<ComTenantDto> getTenant(String url) {

        ComTenantDto cached = cacheUtil.getFullTenant(url);

        if (cached != null) {
            return Mono.just(cached);
        }

        return webClient.get()
                .uri(tenantUrl + "?url=" + url)
                .header("X-Internal-Request", "true")
                .retrieve()
                .bodyToMono(ComTenantDto.class);
    }
}
