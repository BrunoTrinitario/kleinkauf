package com.kleinkauf.gateway.services;

import com.kleinkauf.gateway.config.CaffeineConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// El contador en Redis: lo comparten todos los gateways
class RateLimitCounterTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T10:00:10Z"), ZoneOffset.UTC);
    // Minuto de CLOCK desde 1970: la ventana va en la key
    private static final String KEY = "rate_limit:login:x:" + CLOCK.millis() / 60_000;

    private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ReactiveValueOperations<String, String> values = mock(ReactiveValueOperations.class);
    private final LocalCacheService localCacheService = new LocalCacheService();
    private final RateLimitCounter counter;

    RateLimitCounterTest() {
        when(redis.opsForValue()).thenReturn(values);
        CaffeineConfig config = new CaffeineConfig();
        ReflectionTestUtils.setField(localCacheService, "rateLimitCache", config.rateLimitedCache());
        localCacheService.setClock(CLOCK);
        counter = new RateLimitCounter(redis, localCacheService, CLOCK);
    }

    @Test
    void laPrimeraDeLaVentanaPoneElVencimiento() {
        when(values.increment(KEY)).thenReturn(Mono.just(1L));
        when(redis.expire(KEY, RateLimitCounter.KEY_TTL)).thenReturn(Mono.just(true));

        assertEquals(1L, counter.increment("rate_limit:login:x").block());

        verify(redis).expire(KEY, RateLimitCounter.KEY_TTL);
    }

    // Lo que contaron los otros gateways tambien suma
    @Test
    void lasSiguientesDevuelvenElTotalDeRedis() {
        when(values.increment(KEY)).thenReturn(Mono.just(6L));

        assertEquals(6L, counter.increment("rate_limit:login:x").block());

        verify(redis, never()).expire(anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
    }

    // Con Redis caido o lento el limite sigue, contado en la memoria de este gateway
    @Test
    void sinRedis_cuentaEnMemoria() {
        when(values.increment(KEY)).thenReturn(Mono.error(new IllegalStateException("redis caido")));
        assertEquals(1L, counter.increment("rate_limit:login:x").block());
        assertEquals(2L, counter.increment("rate_limit:login:x").block());

        when(values.increment(KEY)).thenReturn(Mono.never());
        assertEquals(3L, counter.increment("rate_limit:login:x").block(Duration.ofSeconds(5)));
    }
}
