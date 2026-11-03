package com.decor.observability.audit;

import com.decor.common.audit.AuditEvent;
import com.decor.common.constants.TenantConstants;
import com.decor.observability.ws.Channels;
import com.decor.observability.ws.SessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisStreamCommands.XClaimOptions;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/*
Guarda la auditoria que mandan los servicios (stream decor:audit, ver OutboxPublisher en decor-common).
- Grupo de consumidores "observability-audit": con varias instancias cada evento lo guarda una sola, y un
  evento se confirma (XACK) recien despues de guardarlo. Si la base falla queda pendiente y se reintenta.
- Primero lee los pendientes propios (los que no llego a confirmar) y despues los nuevos.
- Cada minuto toma los pendientes de otra instancia que quedaron trabados mas de un minuto (se cayo).
- Un evento ilegible se confirma y se loguea: reintentarlo no lo va a arreglar.
- Despues de guardarlo lo manda en vivo: platform.audit (tenant public) o store.audit (a esa tienda).
observability.audit.enabled=false lo apaga (tests).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "observability.audit.enabled", havingValue = "true", matchIfMissing = true)
public class AuditConsumer implements SmartLifecycle {

    static final String GROUP = "observability-audit";
    private static final int BATCH = 50;
    private static final Duration BLOCK = Duration.ofSeconds(2);
    private static final Duration STALE = Duration.ofMinutes(1);
    private static final Duration ERROR_PAUSE = Duration.ofSeconds(2);
    private static final Duration ERROR_LOG_INTERVAL = Duration.ofMinutes(1);

    private final StringRedisTemplate redis;
    private final AuditStore store;
    private final ActorNames names;
    private final SessionRegistry sessions;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Consumer consumer;

    private volatile boolean running;
    private Thread thread;
    private long lastReclaim;
    private long lastErrorLog;

    public AuditConsumer(StringRedisTemplate redis, AuditStore store, ActorNames names, SessionRegistry sessions,
                         ObjectMapper mapper, Clock clock,
                         @Value("${observability.audit.consumer-name:${HOSTNAME:observability}}") String consumerName) {
        this.redis = redis;
        this.store = store;
        this.names = names;
        this.sessions = sessions;
        this.mapper = mapper;
        this.clock = clock;
        this.consumer = Consumer.from(GROUP, consumerName);
    }

    @Override
    public void start() {
        running = true;
        thread = Thread.ofVirtual().name("audit-consumer").start(this::loop);
    }

    private void loop() {
        boolean groupReady = false;
        while (running) {
            try {
                if (!groupReady) {
                    createGroup();
                    groupReady = true;
                }
                if (clock.millis() - lastReclaim >= STALE.toMillis()) {
                    lastReclaim = clock.millis();
                    reclaimStale();
                }
                // Primero lo que quedo sin confirmar (un error de la base, un reinicio); despues lo nuevo
                if (handle(read(ReadOffset.from("0"), false)) == 0) {
                    handle(read(ReadOffset.lastConsumed(), true));
                }
            } catch (Exception e) {
                if (!running) return;
                logError(e);
                sleep(ERROR_PAUSE);
            }
        }
    }

    // Desde el principio del stream: lo que se publico antes de que existiera el grupo tambien se guarda
    private void createGroup() {
        try {
            redis.execute((RedisCallback<String>) connection -> connection.streamCommands().xGroupCreate(
                    AuditEvent.STREAM.getBytes(StandardCharsets.UTF_8), GROUP, ReadOffset.from("0"), true));
        } catch (RuntimeException e) {
            if (!String.valueOf(rootMessage(e)).contains("BUSYGROUP")) {
                throw e;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<MapRecord<String, Object, Object>> read(ReadOffset offset, boolean block) {
        StreamReadOptions options = StreamReadOptions.empty().count(BATCH);
        if (block) {
            options = options.block(BLOCK);
        }
        List<MapRecord<String, Object, Object>> records = redis.opsForStream()
                .read(consumer, options, StreamOffset.create(AuditEvent.STREAM, offset));
        return records == null ? List.of() : records;
    }

    private int handle(List<MapRecord<String, Object, Object>> records) throws Exception {
        for (MapRecord<String, Object, Object> record : records) {
            process(record.getId(), record.getValue());
        }
        return records.size();
    }

    protected void process(RecordId id, Map<Object, Object> fields) throws Exception {
        AuditEvent event;
        try {
            event = mapper.readValue(String.valueOf(fields.get("payload")), AuditEvent.class);
        } catch (Exception e) {
            log.error("Evento de auditoria ilegible {}, se descarta: {}", id, e.toString());
            redis.opsForStream().acknowledge(AuditEvent.STREAM, GROUP, id);
            return;
        }
        if (!AuditStore.validSchema(event.tenant())) {
            log.error("Evento de auditoria {} con un tenant invalido ({}), se descarta", id, event.tenant());
            redis.opsForStream().acknowledge(AuditEvent.STREAM, GROUP, id);
            return;
        }
        AuditEvent withName = withActorName(event);
        boolean inserted = store.insert(withName);
        redis.opsForStream().acknowledge(AuditEvent.STREAM, GROUP, id);
        if (inserted) {
            if (TenantConstants.PUBLIC.equals(event.tenant())) {
                sessions.publish(Channels.PLATFORM_AUDIT, withName);
            } else {
                sessions.publishToTenant(Channels.STORE_AUDIT, event.tenant(), withName);
            }
        }
    }

    private AuditEvent withActorName(AuditEvent event) {
        AuditEvent.Actor actor = event.actor();
        if (actor == null || actor.name() != null) {
            return event;
        }
        String name = names.name(actor.userId(), event.tenant());
        return new AuditEvent(event.id(), event.tenant(), event.at(), event.service(),
                new AuditEvent.Actor(actor.userId(), name, actor.role()), event.ip(), event.traceId(),
                event.action(), event.entity(), event.entityId(), event.changes());
    }

    // Pendientes de otra instancia que no los confirmo en un minuto: se los queda esta
    private void reclaimStale() {
        PendingMessages pending = redis.opsForStream().pending(AuditEvent.STREAM, GROUP, Range.unbounded(), BATCH);
        List<RecordId> stale = pending.stream()
                .filter(p -> !p.getConsumerName().equals(consumer.getName()))
                .filter(p -> p.getElapsedTimeSinceLastDelivery().compareTo(STALE) >= 0)
                .map(PendingMessage::getId)
                .toList();
        if (!stale.isEmpty()) {
            redis.opsForStream().claim(AuditEvent.STREAM, GROUP, consumer.getName(),
                    XClaimOptions.minIdle(STALE).ids(stale.toArray(RecordId[]::new)));
        }
    }

    private void logError(Exception e) {
        long now = clock.millis();
        if (now - lastErrorLog >= ERROR_LOG_INTERVAL.toMillis()) {
            lastErrorLog = now;
            log.warn("No se pudo guardar la auditoria (se reintenta): {}", e.toString());
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
