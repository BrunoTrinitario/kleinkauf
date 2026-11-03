package com.decor.gateway.security.filters;

import com.decor.common.constants.ActivityKeys;
import com.decor.common.constants.Headers;
import com.decor.common.constants.TenantConstants;
import com.decor.gateway.constants.PermittedUrls;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;

/*
Ultima actividad de los usuarios logueados (docs/observabilidad.md): activity:<tenant>:<userId> en Redis con
la hora de la request, que vence a los 5 minutos. observability la usa para los usuarios conectados.
- Corre despues de JwtFilter (Order 3): X-User-Id ya es el del token (ClientHeadersFilter borra el del cliente).
- Se escribe como mucho una vez cada WRITE_EVERY por usuario, para no ir a Redis en cada request.
- No frena la request: si Redis falla se sigue igual.
 */
@Slf4j
@Component
@Order(4)
public class ActivityFilter implements WebFilter {

    static final Duration WRITE_EVERY = Duration.ofSeconds(30);

    private final ReactiveStringRedisTemplate redis;
    private final Clock clock;
    private final Cache<String, Boolean> recentlyWritten = Caffeine.newBuilder()
            .expireAfterWrite(WRITE_EVERY)
            .maximumSize(100_000)
            .build();

    @Autowired
    public ActivityFilter(ReactiveStringRedisTemplate redis) {
        this(redis, Clock.systemUTC());
    }

    ActivityFilter(ReactiveStringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String userId = exchange.getRequest().getHeaders().getFirst(Headers.USER_ID);
        String tenant = tenantOf(exchange);
        if (userId != null && tenant != null) {
            touch(tenant, userId);
        }
        return chain.filter(exchange);
    }

    private void touch(String tenant, String userId) {
        String key;
        try {
            key = ActivityKeys.key(tenant, Long.parseLong(userId));
        } catch (NumberFormatException e) {
            return;
        }
        if (recentlyWritten.asMap().putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }
        redis.opsForValue()
                .set(key, String.valueOf(clock.millis()), ActivityKeys.TTL)
                .onErrorResume(e -> {
                    log.debug("No se pudo marcar la actividad de {}: {}", key, e.toString());
                    recentlyWritten.invalidate(key);
                    return Mono.empty();
                })
                .subscribe();
    }

    // Plataforma: public (superadmin). Tienda: el schema que puso TenantGatewayFilter
    private static String tenantOf(ServerWebExchange exchange) {
        if (PermittedUrls.isPlatformPath(exchange.getRequest().getURI().getPath())) {
            return TenantConstants.PUBLIC;
        }
        return exchange.getRequest().getHeaders().getFirst(Headers.FORWARDED_HOST);
    }
}
