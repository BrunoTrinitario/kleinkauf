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
import org.springframework.http.HttpHeaders;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;

/*
Last activity (docs/features/observability.md): activity:<tenant>:<userId> in Redis for logged-in users and
visitor:<tenant>:<visitorId> for anonymous visitors of a store, expiring after 5 minutes. observability uses them for
online users. The visitor is a hash of the IP and the User-Agent: the IP itself is not stored.
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
            touchUser(tenant, userId);
        } else if (tenant != null && !TenantConstants.PUBLIC.equals(tenant)) {
            // Without a session, only in a store: the platform has no anonymous visitors to show
            touch(ActivityKeys.visitorKey(tenant, visitorId(exchange)));
        }
        return chain.filter(exchange);
    }

    private void touchUser(String tenant, String userId) {
        try {
            touch(ActivityKeys.key(tenant, Long.parseLong(userId)));
        } catch (NumberFormatException e) {
            // A malformed id is not marked
        }
    }

    // Hash of the connection IP (the same one the rate limit uses, not the client's X-Forwarded-For) and the browser
    static String visitorId(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        String ip = remote == null ? "" : remote.getHostString();
        String agent = String.valueOf(exchange.getRequest().getHeaders().getFirst(HttpHeaders.USER_AGENT));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((ip + "|" + agent).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void touch(String key) {
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
