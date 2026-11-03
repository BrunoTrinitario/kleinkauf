package com.decor.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/*
Manda los pendientes de la outbox a Redis Streams (AuditEvent.STREAM) y los marca como enviados.
- Cada decor.audit.publish-interval (2 s), de a BATCH, hasta vaciarla.
- FOR UPDATE SKIP LOCKED: con varias instancias del servicio cada una toma filas distintas.
- Si Redis falla no se marca nada y se reintenta en la vuelta siguiente. Si una fila se manda dos veces
  (Redis respondio pero el commit fallo) observability la descarta por id.
- Una vez por hora borra los enviados de mas de una semana: la auditoria se guarda en observability, aca
  la tabla es solo de paso.
 */
public class OutboxPublisher implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    static final int BATCH = 100;
    static final long STREAM_MAX_LENGTH = 100_000;
    static final Duration KEEP_SENT = Duration.ofDays(7);
    private static final Duration ERROR_LOG_INTERVAL = Duration.ofMinutes(1);

    private static final String SELECT_PENDING = "SELECT id, payload FROM " + OutboxStore.TABLE
            + " WHERE sent_at IS NULL ORDER BY created_at LIMIT " + BATCH + " FOR UPDATE SKIP LOCKED";
    private static final String MARK_SENT = "UPDATE " + OutboxStore.TABLE + " SET sent_at = ? WHERE id = ?";
    private static final String DELETE_OLD = "DELETE FROM " + OutboxStore.TABLE + " WHERE sent_at IS NOT NULL AND sent_at < ?";

    private final DataSource dataSource;
    private final StringRedisTemplate redis;
    private final Duration interval;
    private final Clock clock;
    private ScheduledExecutorService executor;
    private volatile long lastErrorLog;

    public OutboxPublisher(DataSource dataSource, StringRedisTemplate redis, Duration interval, Clock clock) {
        this.dataSource = dataSource;
        this.redis = redis;
        this.interval = interval;
        this.clock = clock;
    }

    @Override
    public void start() {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "audit-outbox");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::safePublish, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
        executor.scheduleWithFixedDelay(this::safeCleanup, 1, 60, TimeUnit.MINUTES);
    }

    private void safePublish() {
        try {
            while (publishPending() == BATCH) {
                // Habia mas: sigue sin esperar
            }
        } catch (Exception e) {
            logError("No se pudo publicar la auditoria pendiente (se reintenta): {}", e);
        }
    }

    private void safeCleanup() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE_OLD)) {
            statement.setTimestamp(1, Timestamp.from(clock.instant().minus(KEEP_SENT)));
            statement.executeUpdate();
        } catch (Exception e) {
            logError("No se pudo limpiar la outbox de auditoria: {}", e);
        }
    }

    // Cuantas filas mando
    public int publishPending() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                List<String[]> rows = new ArrayList<>();
                try (PreparedStatement select = connection.prepareStatement(SELECT_PENDING);
                     ResultSet result = select.executeQuery()) {
                    while (result.next()) {
                        rows.add(new String[]{result.getString("id"), result.getString("payload")});
                    }
                }
                for (String[] row : rows) {
                    redis.opsForStream().add(
                            StreamRecords.string(Map.of("id", row[0], "payload", row[1])).withStreamKey(AuditEvent.STREAM),
                            XAddOptions.maxlen(STREAM_MAX_LENGTH).approximateTrimming(true));
                }
                if (!rows.isEmpty()) {
                    try (PreparedStatement mark = connection.prepareStatement(MARK_SENT)) {
                        Timestamp now = Timestamp.from(clock.instant());
                        for (String[] row : rows) {
                            mark.setTimestamp(1, now);
                            mark.setString(2, row[0]);
                            mark.addBatch();
                        }
                        mark.executeBatch();
                    }
                }
                connection.commit();
                return rows.size();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    private void logError(String message, Exception e) {
        long now = clock.millis();
        if (now - lastErrorLog >= ERROR_LOG_INTERVAL.toMillis()) {
            lastErrorLog = now;
            log.warn(message, e.toString());
        }
    }

    @Override
    public void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    @Override
    public boolean isRunning() {
        return executor != null;
    }
}
