package com.decor.gateway.security.filters;

import com.decor.common.constants.ActivityKeys;
import com.decor.common.constants.Headers;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityFilterTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ReactiveValueOperations<String, String> values = mock(ReactiveValueOperations.class);
    private final ActivityFilter filter = new ActivityFilter(redis, Clock.fixed(NOW, ZoneOffset.UTC));

    ActivityFilterTest() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.set(anyString(), anyString(), any())).thenReturn(Mono.just(true));
    }

    @Test
    void marcaLaActividadDelUsuarioDeLaTienda() {
        run("/api/v1/products/all", "7", "tienda_a");

        verify(values).set("activity:tienda_a:7", String.valueOf(NOW.toEpochMilli()), ActivityKeys.TTL);
    }

    @Test
    void elSuperadminQuedaEnPublic() {
        run("/api/v1/platform/admin/tenants", "1", null);

        verify(values).set(eq("activity:public:1"), anyString(), eq(ActivityKeys.TTL));
    }

    @Test
    void escribeUnaSolaVezCadaTreintaSegundosPorUsuario() {
        run("/api/v1/products/all", "7", "tienda_a");
        run("/api/v1/products/1", "7", "tienda_a");
        run("/api/v1/products/all", "8", "tienda_a");

        verify(values, times(2)).set(anyString(), anyString(), any());
    }

    @Test
    void sinUsuarioNoMarcaNada() {
        run("/api/v1/catalog/products", null, "tienda_a");
        run("/api/v1/products/all", "no-es-numero", "tienda_a");

        verify(values, never()).set(anyString(), anyString(), any());
    }

    @Test
    void siRedisFallaLaRequestSigue() {
        when(values.set(anyString(), anyString(), any())).thenReturn(Mono.error(new RedisConnectionFailureException("caido")));

        assertThat(run("/api/v1/products/all", "7", "tienda_a")).isTrue();
    }

    private boolean run(String path, String userId, String tenant) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get("http://gateway" + path);
        if (userId != null) request.header(Headers.USER_ID, userId);
        if (tenant != null) request.header(Headers.FORWARDED_HOST, tenant);
        AtomicBoolean passed = new AtomicBoolean();
        filter.filter(MockServerWebExchange.from(request), e -> {
            passed.set(true);
            return Mono.empty();
        }).block();
        return passed.get();
    }
}
