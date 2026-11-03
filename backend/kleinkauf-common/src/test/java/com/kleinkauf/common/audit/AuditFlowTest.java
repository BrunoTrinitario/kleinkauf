package com.kleinkauf.common.audit;

import com.kleinkauf.common.audit.testentities.AuditedThing;
import com.kleinkauf.common.audit.testentities.PlainThing;
import com.kleinkauf.common.constants.Headers;
import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.jpa.boot.spi.IntegratorProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
De punta a punta con H2 y Hibernate de verdad: el cambio, la fila de la outbox en la misma transaccion y
el envio a Redis (mock).
 */
class AuditFlowTest {

    private final ObjectMapper mapper = KleinKaufAuditAutoConfiguration.auditMapper();
    private EmbeddedDatabase db;
    private JdbcTemplate jdbc;
    private EntityManagerFactory emf;
    private TransactionTemplate tx;
    private AuditLog auditLog;

    @BeforeEach
    void setUp() {
        db = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).setName(UUID.randomUUID().toString()).build();
        jdbc = new JdbcTemplate(db);
        OutboxStore outbox = new OutboxStore(mapper);
        outbox.createTable(jdbc);
        AuditEvents events = new AuditEvents("products", Clock.systemUTC());

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(db);
        factory.setPackagesToScan("com.kleinkauf.common.audit.testentities");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        AuditHibernateListener listener = new AuditHibernateListener(events, outbox);
        factory.setJpaPropertyMap(Map.of(
                "hibernate.hbm2ddl.auto", "create-drop",
                "hibernate.integrator_provider", (IntegratorProvider) () -> List.of(new AuditIntegrator(listener))));
        factory.afterPropertiesSet();
        emf = factory.getObject();
        tx = new TransactionTemplate(new JpaTransactionManager(emf));
        auditLog = new AuditLog(events, outbox, jdbc);

        request(r -> {
            r.addHeader(Headers.USER_ID, "3");
            r.addHeader(Headers.CLIENT_IP, "190.1.2.3");
        });
        TenantContext.setCurrentTenant("mi_tienda");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("3", null,
                List.of(new SimpleGrantedAuthority("ROLE_EMPLEADO"), new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        emf.close();
        db.shutdown();
    }

    @Test
    void elAltaQuedaConQuienDesdeDondeYLosCampos() throws Exception {
        Long id = create("Mesa", "15000.00");

        JsonNode event = single();
        assertThat(event.path("action").asText()).isEqualTo("CREATE");
        assertThat(event.path("entity").asText()).isEqualTo("Thing");
        assertThat(event.path("entityId").asText()).isEqualTo(String.valueOf(id));
        assertThat(event.path("tenant").asText()).isEqualTo("mi_tienda");
        assertThat(event.path("service").asText()).isEqualTo("products");
        assertThat(event.path("ip").asText()).isEqualTo("190.1.2.3");
        assertThat(event.path("actor").path("userId").asLong()).isEqualTo(3);
        assertThat(event.path("actor").path("role").asText()).isEqualTo("ADMINISTRADOR");
        assertThat(event.path("at").asText()).endsWith("Z");
        assertThat(event.path("changes").path("price").path("to").asText()).isEqualTo("15000.00");
        // Nunca la contrasena, aunque no este marcada
        assertThat(event.path("changes").has("password")).isFalse();
    }

    @Test
    void enUnCambioVanSoloLosCamposQueCambiaron() throws Exception {
        Long id = create("Mesa", "15000.00");
        clearOutbox();

        update(id, t -> {
            t.price = new BigDecimal("18000.00");
            t.name = "Mesa ratona";
        });

        JsonNode changes = single().path("changes");
        assertThat(changes.size()).isEqualTo(2);
        assertThat(changes.path("price").path("from").asText()).isEqualTo("15000.00");
        assertThat(changes.path("price").path("to").asText()).isEqualTo("18000.00");
        assertThat(changes.path("name").path("from").asText()).isEqualTo("Mesa");
    }

    @Test
    void siSoloCambiaronCamposIgnoradosNoSeRegistra() {
        Long id = create("Mesa", "1");
        clearOutbox();

        update(id, t -> {
            t.lastLogin = LocalDateTime.now();
            t.password = "otra";
        });

        assertThat(count()).isZero();
    }

    @Test
    void elBorradoLogicoEsUnDelete() throws Exception {
        Long id = create("Mesa", "1");
        clearOutbox();

        update(id, t -> t.deletedAt = LocalDateTime.now());

        assertThat(single().path("action").asText()).isEqualTo("DELETE");
    }

    @Test
    void siLaTransaccionSeDeshaceTampocoQuedaLaAuditoria() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            EntityManager em = emf.createEntityManager();
            AuditedThing thing = new AuditedThing();
            thing.name = "Mesa";
            em.joinTransaction();
            em.persist(thing);
            em.flush();
            throw new IllegalStateException("fallo despues del cambio");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(count()).isZero();
    }

    @Test
    void lasLlamadasInternasLoQueNoEsAuditadoYLasImportacionesNoSeRegistran() {
        request(r -> r.addHeader(Headers.INTERNAL_REQUEST, "true"));
        create("Interna", "1");

        request(r -> r.addHeader(Headers.USER_ID, "3"));
        tx.executeWithoutResult(status -> {
            EntityManager em = em();
            PlainThing plain = new PlainThing();
            plain.name = "sin auditar";
            em.persist(plain);
        });
        AuditSuppression.run(() -> create("Importada", "1"));

        assertThat(count()).isZero();
    }

    @Test
    void elAltaPorAutoservicioNoTieneActorYEnLaPlataformaElTenantEsPublic() throws Exception {
        request(r -> r.setRemoteAddr("10.0.0.9"));
        SecurityContextHolder.clearContext();
        TenantContext.clear();

        create("Tienda", "1");

        JsonNode event = single();
        assertThat(event.path("actor").isNull()).isTrue();
        assertThat(event.path("tenant").asText()).isEqualTo("public");
        assertThat(event.path("ip").asText()).isEqualTo("10.0.0.9");
    }

    @Test
    void elRegistroAManoSeSumaALaTransaccion() throws Exception {
        auditLog.read("ProductPricing", 12);
        assertThat(single().path("action").asText()).isEqualTo("READ");
        clearOutbox();

        auditLog.record(AuditEvent.STATUS_CHANGE, "Order", "K7QM-4X2P",
                Map.of("status", AuditLog.change("PENDIENTE", "PAGADO")));
        assertThat(single().path("changes").path("status").path("to").asText()).isEqualTo("PAGADO");
    }

    @Test
    @SuppressWarnings("unchecked")
    void elPublicadorMandaLosPendientesYLosMarca() throws Exception {
        create("Mesa", "1");
        create("Silla", "2");
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        StreamOperations<String, Object, Object> stream = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(stream);
        OutboxPublisher publisher = new OutboxPublisher(db, redis, Duration.ofSeconds(2), Clock.systemUTC());

        assertThat(publisher.publishPending()).isEqualTo(2);
        assertThat(publisher.publishPending()).isZero();
        verify(stream, times(2)).add(any(MapRecord.class), any(XAddOptions.class));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM public.outbox_events WHERE sent_at IS NULL", Long.class)).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void siRedisFallaQuedanPendientes() {
        create("Mesa", "1");
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        StreamOperations<String, Object, Object> stream = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(stream);
        when(stream.add(any(MapRecord.class), any(XAddOptions.class))).thenThrow(new IllegalStateException("redis caido"));
        OutboxPublisher publisher = new OutboxPublisher(db, redis, Duration.ofSeconds(2), Clock.systemUTC());

        assertThatThrownBy(publisher::publishPending).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM public.outbox_events WHERE sent_at IS NULL", Long.class)).isEqualTo(1);
    }

    private Long create(String name, String price) {
        return tx.execute(status -> {
            AuditedThing thing = new AuditedThing();
            thing.name = name;
            thing.price = new BigDecimal(price);
            thing.password = "secreta";
            em().persist(thing);
            return thing.id;
        });
    }

    private void update(Long id, Consumer<AuditedThing> change) {
        tx.executeWithoutResult(status -> change.accept(em().find(AuditedThing.class, id)));
    }

    private EntityManager em() {
        return org.springframework.orm.jpa.EntityManagerFactoryUtils.getTransactionalEntityManager(emf);
    }

    private JsonNode single() throws Exception {
        List<String> payloads = jdbc.queryForList("SELECT payload FROM public.outbox_events", String.class);
        assertThat(payloads).hasSize(1);
        return mapper.readTree(payloads.get(0));
    }

    private long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM public.outbox_events", Long.class);
    }

    private void clearOutbox() {
        jdbc.update("DELETE FROM public.outbox_events");
    }

    private static void request(Consumer<MockHttpServletRequest> setup) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        setup.accept(request);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
