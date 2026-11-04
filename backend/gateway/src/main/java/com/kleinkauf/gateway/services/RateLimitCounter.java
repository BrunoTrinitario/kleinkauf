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
Contador del rate limit en Redis, compartido por todos los gateways (antes cada instancia contaba lo suyo en memoria
y con dos gateways el limite real era el doble). Una key por identificador y ventana de 1 minuto: INCR y, en la
primera, EXPIRE para que se borre sola.
Si Redis falla o tarda, cuenta en la memoria de esta instancia (LocalCacheService): el limite sigue, aunque por gateway.
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

    // Suma la request y devuelve el total de la ventana (incluida esta)
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

    // Una vez por minuto: con Redis caido seria una linea por request
    private void warn(Throwable e) {
        long now = clock.millis();
        long last = lastWarn.get();
        if (now - last >= WARN_EVERY_MILLIS && lastWarn.compareAndSet(last, now)) {
            log.warn("Rate limit sin Redis, cuenta en memoria de este gateway: {}", e.toString());
        }
    }
}
