package com.kleinkauf.observability;

import com.kleinkauf.common.audit.AuditEvent;
import com.kleinkauf.observability.audit.AuditConsumer;
import com.kleinkauf.observability.audit.ActorNames;
import com.kleinkauf.observability.audit.AuditStore;
import com.kleinkauf.observability.dto.PageDto;
import com.kleinkauf.observability.ws.Channels;
import com.kleinkauf.observability.ws.SessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
Guardado y consulta de la auditoria con H2 en modo PostgreSQL, y el consumidor del stream (Redis es un mock).
 */
class AuditStoreTest {

    private static final Instant T0 = Instant.parse("2026-11-02T10:00:00Z");

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private DriverManagerDataSource db;
    private AuditStore store;

    @BeforeEach
    void setUp() {
        db = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        store = new AuditStore(new JdbcTemplate(db), mapper);
    }

    @AfterEach
    void tearDown() {
        new JdbcTemplate(db).execute("SHUTDOWN");
    }

    @Test
    void guardaUnaVezAunqueLlegueRepetidoYCadaTiendaEnSuSchema() throws Exception {
        AuditEvent price = event("mi_tienda", T0, 3L, "UPDATE", "Product", "12");

        assertThat(store.insert(price)).isTrue();
        assertThat(store.insert(price)).isFalse();
        store.insert(event("otra_tienda", T0, 5L, "CREATE", "Product", "1"));

        PageDto<AuditEvent> mine = store.page("mi_tienda", query(null, null, null, null), 0, 20);
        assertThat(mine.totalItems()).isEqualTo(1);
        AuditEvent saved = mine.items().get(0);
        assertThat(saved.actor().name()).isEqualTo("Ana");
        assertThat(saved.changes().get("price").to()).isEqualTo("18000.00");
        assertThat(saved.at()).isEqualTo(T0);
    }

    @Test
    void filtraPorFechaUsuarioEntidadYAccionYPagina() throws Exception {
        for (int i = 0; i < 25; i++) {
            store.insert(event("mi_tienda", T0.plusSeconds(i), i % 2 == 0 ? 3L : 4L, "UPDATE", "Product", String.valueOf(i % 3)));
        }
        store.insert(event("mi_tienda", T0.plusSeconds(100), 3L, "READ", "ProductPricing", "1"));

        PageDto<AuditEvent> first = store.page("mi_tienda", query(null, null, null, null), 0, 10);
        assertThat(first.totalItems()).isEqualTo(26);
        assertThat(first.totalPages()).isEqualTo(3);
        // Lo mas nuevo primero
        assertThat(first.items().get(0).action()).isEqualTo("READ");

        assertThat(store.page("mi_tienda", query(null, null, 4L, null), 0, 50).totalItems()).isEqualTo(12);
        assertThat(store.page("mi_tienda", new AuditStore.Query(null, null, null, "Product", "0", null), 0, 50).totalItems()).isEqualTo(9);
        assertThat(store.page("mi_tienda", query(null, null, null, "READ"), 0, 50).totalItems()).isEqualTo(1);
        assertThat(store.page("mi_tienda", query(T0.plusSeconds(20), T0.plusSeconds(25), null, null), 0, 50).totalItems()).isEqualTo(5);
        // Una tienda sin eventos: vacio, no error
        assertThat(store.page("nueva", query(null, null, null, null), 0, 50).totalItems()).isZero();
    }

    @Test
    void unSchemaInvalidoNoLlegaAlSql() {
        assertThatThrownBy(() -> store.page("x; drop table y", query(null, null, null, null), 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(AuditStore.validSchema("Public")).isFalse();
        assertThat(AuditStore.validSchema("mi_tienda")).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void elConsumidorGuardaConfirmaYMandaSoloALaTiendaDelEvento() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        StreamOperations<String, Object, Object> stream = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(stream);
        SessionRegistry sessions = mock(SessionRegistry.class);
        ActorNames names = mock(ActorNames.class);
        when(names.name(3L, "mi_tienda")).thenReturn("Ana");
        TestableConsumer consumer = new TestableConsumer(redis, store, names, sessions, mapper);

        AuditEvent withoutName = new AuditEvent("e1", "mi_tienda", T0, "products",
                new AuditEvent.Actor(3L, null, "ADMINISTRADOR"), "1.2.3.4", null, "UPDATE", "Product", "12", Map.of());
        Map<Object, Object> fields = Map.of("id", "e1", "payload", mapper.writeValueAsString(withoutName));

        consumer.receive(RecordId.of("1-0"), fields);
        consumer.receive(RecordId.of("1-1"), fields);

        verify(stream, times(2)).acknowledge(eq(AuditEvent.STREAM), anyString(), any(RecordId[].class));
        // Repetido: se confirma pero no se manda de nuevo
        verify(sessions, times(1)).publishToTenant(eq(Channels.STORE_AUDIT), eq("mi_tienda"), any());
        assertThat(store.page("mi_tienda", query(null, null, null, null), 0, 10).items().get(0).actor().name())
                .isEqualTo("Ana");
    }

    @Test
    @SuppressWarnings("unchecked")
    void laAuditoriaDeLaPlataformaVaAlCanalDeLaPlataformaYLoIlegibleSeDescarta() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        StreamOperations<String, Object, Object> stream = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(stream);
        SessionRegistry sessions = mock(SessionRegistry.class);
        TestableConsumer consumer = new TestableConsumer(redis, store, mock(ActorNames.class), sessions, mapper);

        AuditEvent selfService = new AuditEvent("p1", "public", T0, "users", null, "1.2.3.4", null,
                "CREATE", "Tenant", "7", Map.of());
        consumer.receive(RecordId.of("2-0"), Map.of("payload", mapper.writeValueAsString(selfService)));
        consumer.receive(RecordId.of("2-1"), Map.of("payload", "no es json"));

        verify(sessions).publish(eq(Channels.PLATFORM_AUDIT), any());
        verify(sessions, never()).publishToTenant(anyString(), anyString(), any());
        verify(stream, times(2)).acknowledge(eq(AuditEvent.STREAM), anyString(), any(RecordId[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void siLaBaseFallaNoSeConfirma() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        StreamOperations<String, Object, Object> stream = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(stream);
        AuditStore broken = mock(AuditStore.class);
        when(broken.insert(any())).thenThrow(new IllegalStateException("base caida"));
        TestableConsumer consumer = new TestableConsumer(redis, broken, mock(ActorNames.class), mock(SessionRegistry.class), mapper);

        AuditEvent event = event("mi_tienda", T0, 3L, "UPDATE", "Product", "1");
        assertThatThrownBy(() -> consumer.receive(RecordId.of("3-0"),
                Map.of("payload", mapper.writeValueAsString(event)))).isInstanceOf(IllegalStateException.class);

        verify(stream, never()).acknowledge(anyString(), anyString(), any(RecordId[].class));
    }

    static class TestableConsumer extends AuditConsumer {
        TestableConsumer(StringRedisTemplate redis, AuditStore store, ActorNames names, SessionRegistry sessions, ObjectMapper mapper) {
            super(redis, store, names, sessions, mapper, Clock.systemUTC(), "test");
        }

        void receive(RecordId id, Map<Object, Object> fields) throws Exception {
            process(id, fields);
        }
    }

    private static AuditStore.Query query(Instant from, Instant to, Long userId, String action) {
        return new AuditStore.Query(from, to, userId, null, null, action);
    }

    private static AuditEvent event(String tenant, Instant at, Long userId, String action, String entity, String entityId) {
        return new AuditEvent(UUID.randomUUID().toString(), tenant, at, "products",
                new AuditEvent.Actor(userId, "Ana", "ADMINISTRADOR"), "190.1.2.3", "4bf92f3577b34da6",
                action, entity, entityId, Map.of("price", new AuditEvent.Change("15000.00", "18000.00")));
    }
}
