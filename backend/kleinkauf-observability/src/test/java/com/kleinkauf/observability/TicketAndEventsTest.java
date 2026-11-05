package com.kleinkauf.observability;

import com.kleinkauf.common.events.KleinKaufEvent;
import com.kleinkauf.observability.events.EventStreamListener;
import com.kleinkauf.observability.ws.Channels;
import com.kleinkauf.observability.ws.SessionRegistry;
import com.kleinkauf.observability.ws.TicketStore;
import com.kleinkauf.observability.ws.WsTicket;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketAndEventsTest {

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    @SuppressWarnings("unchecked")
    void ticketExpiresAndIsUsedOnlyOnce() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        TicketStore store = new TicketStore(redis, mapper, Duration.ofSeconds(30));

        String id = store.issue(new WsTicket(1L, "public"));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq("ws-ticket:" + id), json.capture(), eq(Duration.ofSeconds(30)));
        assertThat(id).hasSizeGreaterThanOrEqualTo(40);

        when(values.getAndDelete("ws-ticket:" + id)).thenReturn(json.getValue(), (String) null);
        assertThat(store.redeem(id)).contains(new WsTicket(1L, "public"));
        assertThat(store.redeem(id)).isEmpty();
        assertThat(store.redeem(null)).isEmpty();
        assertThat(store.redeem("x".repeat(500))).isEmpty();
    }

    @Test
    void storeEventsGoToTheBusinessChannel() {
        SessionRegistry sessions = mock(SessionRegistry.class);
        TestableListener listener = new TestableListener(sessions);
        KleinKaufEvent created = new KleinKaufEvent(KleinKaufEvent.TENANT_CREATED, "public",
                Instant.parse("2026-11-02T10:00:00Z"), Map.of("schema", "mi_tienda"));

        listener.receive(created.toFields(mapper));
        listener.receive(Map.of("cualquier", "cosa"));
        listener.receive(new KleinKaufEvent("order.created", "demo", Instant.now(), Map.of()).toFields(mapper));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(sessions).publish(eq(Channels.PLATFORM_BUSINESS), data.capture());
        assertThat(data.getValue()).containsEntry("type", "tenant.created").containsEntry("schema", "mi_tienda");
    }

    @Test
    void ordersAndStockGoOnlyToTheirStoreActivity() {
        SessionRegistry sessions = mock(SessionRegistry.class);
        TestableListener listener = new TestableListener(sessions);

        listener.receive(new KleinKaufEvent(KleinKaufEvent.ORDER_CREATED, "demo", Instant.parse("2026-11-02T10:00:00Z"),
                Map.of("number", "K7QM-4X2P", "channel", "ONLINE")).toFields(mapper));
        listener.receive(new KleinKaufEvent(KleinKaufEvent.STOCK_LOW, "demo", Instant.now(), Map.of("productId", 12)).toFields(mapper));
        // A store event with tenant public belongs to no store; an unknown one is not forwarded
        listener.receive(new KleinKaufEvent(KleinKaufEvent.STOCK_LOW, "public", Instant.now(), Map.of()).toFields(mapper));
        listener.receive(new KleinKaufEvent("order.otra-cosa", "demo", Instant.now(), Map.of()).toFields(mapper));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(sessions, times(2)).publishToTenant(eq(Channels.STORE_ACTIVITY), eq("demo"), data.capture());
        assertThat(data.getAllValues().get(0)).containsEntry("type", "order.created").containsEntry("number", "K7QM-4X2P");
        assertThat(data.getAllValues().get(1)).containsEntry("type", "stock.low");
        verify(sessions, never()).publish(anyString(), any());
    }

    @Test
    void withoutSubscribersNoMessageIsBuilt() {
        SessionRegistry sessions = new SessionRegistry(mapper, Clock.systemUTC());
        sessions.publish(Channels.PLATFORM_HEALTH, Map.of());
        assertThat(sessions.clients()).isEmpty();
    }

    // Exposes the handling of a record without starting the Redis container
    static class TestableListener extends EventStreamListener {
        TestableListener(SessionRegistry sessions) {
            super(mock(RedisConnectionFactory.class), sessions,
                    JsonMapper.builder().findAndAddModules().build(), Clock.systemUTC());
        }

        void receive(Map<String, String> fields) {
            handle(fields);
        }
    }

    @Test
    void malformedEventIsNotForwarded() {
        SessionRegistry sessions = mock(SessionRegistry.class);
        new TestableListener(sessions).receive(Map.of("type", "tenant.created"));
        verify(sessions, never()).publish(anyString(), any());
    }
}
