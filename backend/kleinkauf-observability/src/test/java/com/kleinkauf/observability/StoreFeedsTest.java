package com.kleinkauf.observability;

import com.kleinkauf.common.constants.UsageKeys;
import com.kleinkauf.observability.audit.ActorNames;
import com.kleinkauf.observability.collector.Traffic;
import com.kleinkauf.observability.dto.StoreUsageDto;
import com.kleinkauf.observability.dto.StoreUsersDto;
import com.kleinkauf.observability.store.CatalogUsageReader;
import com.kleinkauf.observability.store.StoreFeeds;
import com.kleinkauf.observability.store.StoreUsers;
import com.kleinkauf.observability.ws.Channels;
import com.kleinkauf.observability.ws.SessionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Canales de la tienda que arma el recolector: store.users y store.usage
class StoreFeedsTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ActorNames names = mock(ActorNames.class);
    private final SessionRegistry sessions = mock(SessionRegistry.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T13:00:00Z"), ZoneOffset.UTC);
    private final StoreFeeds feeds = new StoreFeeds(sessions, new StoreUsers(redis, names), new CatalogUsageReader(redis), clock);

    @Test
    @SuppressWarnings("unchecked")
    void usuariosConectados_delMasRecienteConNombre() {
        scan(List.of("activity:demo:7", "activity:demo:8", "activity:demo:9"));
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        // El 9 vencio entre el SCAN y la lectura
        when(values.multiGet(List.of("activity:demo:7", "activity:demo:8", "activity:demo:9")))
                .thenReturn(java.util.Arrays.asList("1000", "5000", null));
        when(names.name(7L, "demo")).thenReturn("Ana");
        when(names.name(8L, "demo")).thenReturn("Beto");

        StoreUsersDto users = feeds.users("demo");

        assertThat(users.total()).isEqualTo(2);
        assertThat(users.users()).containsExactly(
                new StoreUsersDto.ConnectedUser(8L, "Beto", Instant.ofEpochMilli(5000)),
                new StoreUsersDto.ConnectedUser(7L, "Ana", Instant.ofEpochMilli(1000)));
    }

    @Test
    void usuariosConectados_unTenantSinFormaDeSchemaNoVaAlScan() {
        assertThat(feeds.users("demo*").total()).isZero();
        verify(redis, never()).scan(any(ScanOptions.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void uso_visitasLos5MasVistosYElTraficoDeLaTienda() {
        HashOperations<String, Object, Object> hash = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hash);
        Map<Object, Object> counts = new LinkedHashMap<>();
        counts.put("visits", "40");
        for (int i = 1; i <= 6; i++) counts.put("product:mesa-" + i, String.valueOf(i * 10));
        when(hash.entries(UsageKeys.key("demo", UsageKeys.today()))).thenReturn(counts);

        // Sin dos lecturas del gateway todavia: sin trafico
        assertThat(feeds.usage("demo").traffic()).isNull();

        feeds.update(Map.of("demo", new Traffic.TenantRates(2.5, 0.1, 0)));
        StoreUsageDto usage = feeds.usage("demo");

        assertThat(usage.catalogVisits()).isEqualTo(40);
        assertThat(usage.topProducts()).hasSize(5);
        assertThat(usage.topProducts().get(0)).isEqualTo(new StoreUsageDto.ProductViews("mesa-6", 60));
        assertThat(usage.traffic()).isEqualTo(new StoreUsageDto.Traffic(2.5, 0.1, 0));
        // Otra tienda sin requests en el intervalo: 0, no null
        when(hash.entries(UsageKeys.key("otra", UsageKeys.today()))).thenReturn(Map.of());
        assertThat(feeds.usage("otra").traffic()).isEqualTo(new StoreUsageDto.Traffic(0, 0, 0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void update_mandaSoloALasTiendasSuscriptasYSiRedisFallaNoManda() {
        HashOperations<String, Object, Object> hash = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hash);
        when(hash.entries(anyString())).thenReturn(Map.of("visits", "3"));
        when(sessions.tenantsSubscribedTo(Channels.STORE_USAGE)).thenReturn(Set.of("demo"));
        when(sessions.tenantsSubscribedTo(Channels.STORE_USERS)).thenReturn(Set.of());

        feeds.update(Map.of());

        verify(sessions).publishToTenant(eq(Channels.STORE_USAGE), eq("demo"), any(StoreUsageDto.class));
        verify(sessions, never()).publishToTenant(eq(Channels.STORE_USERS), anyString(), any());

        when(sessions.tenantsSubscribedTo(Channels.STORE_USAGE)).thenReturn(Set.of("caida"));
        when(hash.entries(UsageKeys.key("caida", UsageKeys.today()))).thenThrow(new RedisConnectionFailureException("caido"));
        feeds.update(Map.of());
        verify(sessions, never()).publishToTenant(eq(Channels.STORE_USAGE), eq("caida"), any());
    }

    @SuppressWarnings("unchecked")
    private void scan(List<String> keys) {
        Cursor<String> cursor = mock(Cursor.class);
        Iterator<String> iterator = keys.iterator();
        doAnswer(i -> {
            iterator.forEachRemaining(i.getArgument(0));
            return null;
        }).when(cursor).forEachRemaining(any());
        when(redis.scan(any(ScanOptions.class))).thenReturn(cursor);
    }
}
