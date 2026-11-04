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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
Guardado y consulta de la auditoria contra un Postgres real (la tabla esta particionada por mes, H2 no lo soporta),
y el consumidor del stream (Redis es un mock). Sin Docker se saltea.
Cada test arranca con los schemas vacios.
 */
@Testcontainers(disabledWithoutDocker = true)
class AuditStoreTest {

    private static final Instant T0 = Instant.parse("2026-11-02T10:00:00Z");
    private static final List<String> SCHEMAS = List.of("mi_tienda", "otra_tienda", "nueva", "vieja");

    // Uno para toda la clase: levantarlo por test es lento
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
        }
    }

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private JdbcTemplate jdbc;
    private AuditStore store;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        for (String schema : SCHEMAS) {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
        jdbc.execute("DROP TABLE IF EXISTS public.audit_events CASCADE");
        store = new AuditStore(jdbc, mapper);
    }

    // Cada mes en su particion; una consulta por fechas lee igual de varias
    @Test
    void cadaMesVaASuParticion() throws Exception {
        store.insert(event("mi_tienda", Instant.parse("2026-10-31T23:59:59Z"), 3L, "UPDATE", "Product", "1"));
        store.insert(event("mi_tienda", Instant.parse("2026-11-01T00:00:00Z"), 3L, "UPDATE", "Product", "1"));
        store.insert(event("mi_tienda", Instant.parse("2027-01-15T10:00:00Z"), 3L, "UPDATE", "Product", "1"));

        assertThat(rows("mi_tienda.audit_events_2026_10")).isEqualTo(1);
        assertThat(rows("mi_tienda.audit_events_2026_11")).isEqualTo(1);
        assertThat(rows("mi_tienda.audit_events_2027_01")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT c.relkind FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                + "WHERE n.nspname = 'mi_tienda' AND c.relname = 'audit_events'", String.class)).isEqualTo("p");
        assertThat(store.page("mi_tienda", query(Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-12-01T00:00:00Z"),
                null, null), 0, 10).totalItems()).isEqualTo(2);
    }

    // La tabla de antes (sin particiones) se pasa a particionada la primera vez, sin perder eventos
    @Test
    void laTablaViejaSePasaAParticionadaConSusEventos() throws Exception {
        jdbc.execute("CREATE SCHEMA vieja");
        jdbc.execute("CREATE TABLE vieja.audit_events (id VARCHAR(36) PRIMARY KEY, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL, "
                + "service VARCHAR(40), actor_user_id BIGINT, actor_name VARCHAR(200), actor_role VARCHAR(40), ip VARCHAR(64), "
                + "trace_id VARCHAR(64), action VARCHAR(40) NOT NULL, entity VARCHAR(60) NOT NULL, entity_id VARCHAR(100), "
                + "changes TEXT, received_at TIMESTAMP WITH TIME ZONE NOT NULL)");
        jdbc.execute("CREATE INDEX audit_events_at ON vieja.audit_events (occurred_at)");
        jdbc.update("INSERT INTO vieja.audit_events VALUES ('a', '2026-09-10 12:00:00+00', 'products', 3, 'Ana', 'ADMINISTRADOR', "
                + "null, null, 'UPDATE', 'Product', '1', '{}', now())");
        jdbc.update("INSERT INTO vieja.audit_events VALUES ('b', '2026-10-31 23:30:00+00', 'products', 3, 'Ana', 'ADMINISTRADOR', "
                + "null, null, 'DELETE', 'Product', '2', '{}', now())");

        PageDto<AuditEvent> page = store.page("vieja", query(null, null, null, null), 0, 10);

        assertThat(page.totalItems()).isEqualTo(2);
        assertThat(page.items().get(0).action()).isEqualTo("DELETE");
        assertThat(rows("vieja.audit_events_2026_09")).isEqualTo(1);
        assertThat(rows("vieja.audit_events_2026_10")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT to_regclass('vieja.audit_events_legacy')", String.class)).isNull();
        // Y sigue guardando, tambien en un mes nuevo
        assertThat(store.insert(event("vieja", T0, 3L, "CREATE", "Product", "3"))).isTrue();
        assertThat(store.page("vieja", query(null, null, null, null), 0, 10).totalItems()).isEqualTo(3);
    }

    @Test
    void exportaTodoLoFiltradoDeLoMasNuevoALoMasViejo() throws Exception {
        for (int i = 0; i < 5; i++) {
            store.insert(event("mi_tienda", T0.plusSeconds(i), 3L, i == 4 ? "DELETE" : "UPDATE", "Product", String.valueOf(i)));
        }
        List<AuditEvent> exported = new ArrayList<>();

        store.forEach("mi_tienda", query(null, null, null, "UPDATE"), 100, exported::add);

        assertThat(store.count("mi_tienda", query(null, null, null, "UPDATE"))).isEqualTo(4);
        assertThat(exported).extracting(AuditEvent::entityId).containsExactly("3", "2", "1", "0");
    }

    private long rows(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
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
