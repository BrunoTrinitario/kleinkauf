package com.decor.common.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DecorEventPublisherTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    private final ObjectMapper mapper = new ObjectMapper();
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final StreamOperations<String, Object, Object> stream = mock(StreamOperations.class);
    private final DecorEventPublisher publisher =
            new DecorEventPublisher(redis, mapper, Clock.fixed(NOW, ZoneOffset.UTC));

    DecorEventPublisherTest() {
        when(redis.opsForStream()).thenReturn(stream);
    }

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void elEventoIdaYVueltaPorLosCamposDelStream() {
        DecorEvent event = new DecorEvent(DecorEvent.TENANT_CREATED, "public", NOW, Map.of("schema", "mi_tienda", "id", 7));

        DecorEvent back = DecorEvent.fromFields(event.toFields(mapper), mapper);

        assertThat(back).isEqualTo(event);
        assertThat(DecorEvent.fromFields(Map.of("type", "x"), mapper)).isNull();
        assertThat(DecorEvent.fromFields(Map.of("type", "x", "tenant", "t", "at", "no es fecha"), mapper)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void publicaEnElStreamConRecorte() {
        publisher.publish(DecorEvent.TENANT_CREATED, "public", Map.of("schema", "mi_tienda"));

        verify(stream).add(any(MapRecord.class), any(XAddOptions.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void enUnaTransaccionPublicaRecienDespuesDelCommit() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish(DecorEvent.TENANT_DELETED, "public", Map.of());
        verify(stream, never()).add(any(MapRecord.class), any(XAddOptions.class));

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(stream).add(any(MapRecord.class), any(XAddOptions.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void siRedisFallaLaOperacionSigue() {
        when(stream.add(any(MapRecord.class), any(XAddOptions.class)))
                .thenThrow(new RedisConnectionFailureException("caido"));

        assertThatCode(() -> publisher.publish(DecorEvent.TENANT_ENABLED, "public", Map.of())).doesNotThrowAnyException();
    }
}
