package com.decor.gateway.services;

import com.decor.gateway.dtos.TenantDto;
import com.decor.gateway.util.CacheUtil;
import com.decor.gateway.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.lang.annotation.IncompleteAnnotationException;

@Service
public class TenantService {

    @Autowired
    private CacheUtil cacheUtil;

    @Value("${tenant.url}")
    private String tenantUrl;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    public LocalCacheService localCacheService;

    private final WebClient webClient;

    public TenantService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public Mono<TenantDto> getTenant(String schema) {

        TenantDto cached = localCacheService.getTenant(schema);
        if (cached != null) {
            return Mono.just(cached);
        }

        cached = cacheUtil.getTenantBySchema(schema);
        if (cached != null) {
            return Mono.just(cached);
        }

        return webClient.get()
                .uri(tenantUrl + "/id?url=" + schema)
                .header("X-Internal-Request", "true")
                .header("X-Internal-Token", jwtUtil.generateMicroServiceToken("gateway"))
                .retrieve()
                .bodyToMono(TenantDto.class);
    }

    public boolean getTenantForCorsSync(String url) {
        boolean cached = false;

        cached = localCacheService.existsUrl(url);
        if (cached) return true;

        cached = cacheUtil.existsTenantByUrl(url);
        if (cached) return true;

        Boolean allowed = webClient.get()
                .uri(tenantUrl + "/cors?url=" + url)
                .header("X-Internal-Request", "true")
                .header("X-Internal-Token",
                        jwtUtil.generateMicroServiceToken("gateway"))
                .retrieve()
                .bodyToMono(Boolean.class)
                .onErrorReturn(false)
                .block();

        localCacheService.saveCorsTenant(url);

        return Boolean.TRUE.equals(allowed);
    }
}
