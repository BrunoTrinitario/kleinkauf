package com.kleinkauf.gateway.security.filters;

import com.kleinkauf.common.constants.ActivityKeys;
import com.kleinkauf.common.constants.Headers;
import com.kleinkauf.common.constants.TenantConstants;
import com.kleinkauf.gateway.constants.PermittedUrls;
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
Last activity of logged-in users (docs/observabilidad.md): activity:<tenant>:<userId> in Redis with
the time of the request, expiring after 5 minutes. observability uses it for the online users.
- Runs after JwtFilter (Order 3): X-User-Id is already the token's (ClientHeadersFilter removes the client's).
- Written at most once every WRITE_EVERY per user, so as not to hit Redis on every request.
- Does not hold the request back: if Redis fails it goes on anyway.
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

    // Platform: public (superadmin). Store: the schema set by TenantGatewayFilter
    private static String tenantOf(ServerWebExchange exchange) {
        if (PermittedUrls.isPlatformPath(exchange.getRequest().getURI().getPath())) {
            return TenantConstants.PUBLIC;
        }
        return exchange.getRequest().getHeaders().getFirst(Headers.FORWARDED_HOST);
    }
}
