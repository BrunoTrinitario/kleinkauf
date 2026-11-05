package com.kleinkauf.observability;

import com.kleinkauf.observability.collector.Collector;
import com.kleinkauf.observability.collector.ConnectedUsers;
import com.kleinkauf.observability.collector.InstanceMetrics;
import com.kleinkauf.observability.collector.InstanceProbe;
import com.kleinkauf.observability.collector.InstanceProbe.Result;
import com.kleinkauf.observability.collector.PlatformState;
import com.kleinkauf.observability.collector.TargetDiscovery;
import com.kleinkauf.observability.collector.TargetDiscovery.Target;
import com.kleinkauf.observability.collector.Traffic;
import com.kleinkauf.observability.dto.ConnectedUsersDto;
import com.kleinkauf.observability.dto.PlatformOverviewDto;
import com.kleinkauf.observability.dto.ServiceHealthDto;
import com.kleinkauf.observability.dto.ServiceTrafficDto;
import com.kleinkauf.observability.store.StoreFeeds;
import com.kleinkauf.observability.ws.Channels;
import com.kleinkauf.observability.ws.SessionRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectorTest {

    private static final Target PRODUCTS_1 = new Target("products-microservice", "p1", "http://10.0.0.1:9002");
    private static final Target PRODUCTS_2 = new Target("products-microservice", "p2", "http://10.0.0.2:9002");

    private final TargetDiscovery discovery = mock(TargetDiscovery.class);
    private final InstanceProbe probe = mock(InstanceProbe.class);
    private final ConnectedUsers connectedUsers = mock(ConnectedUsers.class);
    private final SessionRegistry sessions = mock(SessionRegistry.class);
    private final StoreFeeds storeFeeds = mock(StoreFeeds.class);
    private final PlatformState state = new PlatformState();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-02T10:00:00Z"));
    private final Collector collector = new Collector(discovery, probe, connectedUsers, state, sessions, storeFeeds, clock,
            Duration.ofSeconds(2), "gateway-microservice");

    CollectorTest() {
        when(discovery.targets()).thenReturn(List.of(PRODUCTS_1, PRODUCTS_2));
        when(discovery.services(any())).thenReturn(List.of("users-microservice", "products-microservice"));
        when(connectedUsers.count()).thenReturn(new ConnectedUsersDto(3, List.of(new ConnectedUsersDto.TenantUsers("demo", 3))));
    }

    @Test
    void buildsHealthPerService() {
        when(probe.probe(PRODUCTS_1)).thenReturn(up(PRODUCTS_1, 100));
        when(probe.probe(PRODUCTS_2)).thenReturn(new Result(PRODUCTS_2, ServiceHealthDto.DOWN,
                Map.of("db", "DOWN"), null, null));

        PlatformOverviewDto overview = collector.collect();

        ServiceHealthDto users = overview.services().get(0);
        ServiceHealthDto products = overview.services().get(1);
        // users has no instances in eureka: it shows as down
        assertThat(users.status()).isEqualTo(ServiceHealthDto.DOWN);
        assertThat(users.instances()).isEmpty();
        assertThat(products.status()).isEqualTo(ServiceHealthDto.DEGRADED);
        assertThat(products.instances()).hasSize(2);
        assertThat(overview.connectedUsers().total()).isEqualTo(3);
        assertThat(state.overview()).isSameAs(overview);
    }

    @Test
    void ratesAppearFromTheSecondReading() {
        when(probe.probe(PRODUCTS_1)).thenReturn(up(PRODUCTS_1, 100), up(PRODUCTS_1, 300));
        when(probe.probe(PRODUCTS_2)).thenReturn(up(PRODUCTS_2, 50), up(PRODUCTS_2, 150));

        ServiceTrafficDto first = collector.collect().traffic().get(1);
        clock.advance(Duration.ofSeconds(10));
        ServiceTrafficDto second = collector.collect().traffic().get(1);

        assertThat(first.requestsPerSecond()).isNull();
        // (200 + 100) requests in 10 s between the two instances
        assertThat(second.requestsPerSecond()).isEqualTo(30.0);
        assertThat(second.instances()).isEqualTo(2);
    }

    @Test
    void healthIsSentOnlyIfChangedAndTrafficAlways() {
        when(probe.probe(PRODUCTS_1)).thenReturn(up(PRODUCTS_1, 1), up(PRODUCTS_1, 2),
                new Result(PRODUCTS_1, ServiceHealthDto.DOWN, Map.of(), "Sin respuesta de /ready: ConnectException", null));
        when(probe.probe(PRODUCTS_2)).thenReturn(up(PRODUCTS_2, 1));

        collector.collect();
        collector.collect();
        collector.collect();

        verify(sessions, times(2)).publish(eq(Channels.PLATFORM_HEALTH), any());
        verify(sessions, times(3)).publish(eq(Channels.PLATFORM_TRAFFIC), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void perStoreTrafficComesOnlyFromTheGatewayFromTheSecondReading() {
        Target gateway = new Target("gateway-microservice", "g1", "http://10.0.0.3:9000");
        when(discovery.targets()).thenReturn(List.of(PRODUCTS_1, gateway));
        when(probe.probe(PRODUCTS_1)).thenReturn(withTenant(PRODUCTS_1, 0), withTenant(PRODUCTS_1, 999));
        when(probe.probe(gateway)).thenReturn(withTenant(gateway, 100), withTenant(gateway, 150));

        collector.collect();
        clock.advance(Duration.ofSeconds(10));
        collector.collect();

        ArgumentCaptor<Map<String, Traffic.TenantRates>> rates = ArgumentCaptor.forClass(Map.class);
        verify(storeFeeds, times(2)).update(rates.capture());
        // First reading: no rates yet
        assertThat(rates.getAllValues().get(0)).isNull();
        // 50 requests in 10 s; the products ones do not add up
        assertThat(rates.getAllValues().get(1)).containsEntry("demo", new Traffic.TenantRates(5.0, 0.0, 0.0));
    }

    private static Result withTenant(Target target, double requests) {
        InstanceMetrics metrics = MetricsParsingTest.tenantMetrics(Map.of("demo", new InstanceMetrics.TenantRequests(requests, 0, 0)));
        return new Result(target, ServiceHealthDto.UP, Map.of(), null, metrics);
    }

    private static Result up(Target target, double requests) {
        InstanceMetrics metrics = MetricsParsingTest.metrics(requests, 0, Map.of(Double.POSITIVE_INFINITY, requests));
        return new Result(target, ServiceHealthDto.UP, Map.of("db", "UP"), null, metrics);
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
