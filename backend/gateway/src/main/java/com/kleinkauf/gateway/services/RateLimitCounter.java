package com.kleinkauf.gateway.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/*
Rate limit counter in Redis, shared by all gateways (before, each instance counted its own in memory
and with two gateways the real limit was double). One key per identifier and 1-minute window: INCR and, on the
first one, EXPIRE so it deletes itself.
If Redis fails or is slow, it counts in this instance's memory (LocalCacheService): the limit still holds, although per gateway.
 */
@Slf4j
@Service
public class RateLimitCounter {

    static final Duration KEY_TTL = Duration.ofMinutes(2);
    static final Duration REDIS_TIMEOUT = Duration.ofMillis(300);
    private static final long WARN_EVERY_MILLIS = 60_000;

    private final ReactiveStringRedisTemplate redis;
    private final LocalCacheService localCacheService;
    private final Clock clock;
    private final AtomicLong lastWarn = new AtomicLong();

    @Autowired
    public RateLimitCounter(ReactiveStringRedisTemplate redis, LocalCacheService localCacheService) {
        this(redis, localCacheService, Clock.systemUTC());
    }

    RateLimitCounter(ReactiveStringRedisTemplate redis, LocalCacheService localCacheService, Clock clock) {
        this.redis = redis;
        this.localCacheService = localCacheService;
        this.clock = clock;
    }

    // Adds the request and returns the window's total (including this one)
    public Mono<Long> increment(String requestIdentifier) {
        String key = requestIdentifier + ":" + clock.millis() / 60_000;
        return redis.opsForValue().increment(key)
                .flatMap(count -> count == 1 ? redis.expire(key, KEY_TTL).thenReturn(count) : Mono.just(count))
                .timeout(REDIS_TIMEOUT)
                .onErrorResume(e -> {
                    warn(e);
                    return Mono.just((long) localCacheService.rateLimitCache(requestIdentifier));
                });
    }

    // Once a minute: with Redis down it would be one line per request
    private void warn(Throwable e) {
        long now = clock.millis();
        long last = lastWarn.get();
        if (now - last >= WARN_EVERY_MILLIS && lastWarn.compareAndSet(last, now)) {
            log.warn("Rate limit sin Redis, cuenta en memoria de este gateway: {}", e.toString());
        }
    }
}
