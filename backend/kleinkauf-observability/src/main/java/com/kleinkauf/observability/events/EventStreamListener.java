package com.kleinkauf.observability.events;

import com.kleinkauf.common.constants.TenantConstants;
import com.kleinkauf.common.events.KleinKaufEvent;
import com.kleinkauf.observability.ws.Channels;
import com.kleinkauf.observability.ws.SessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamReadRequest;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/*
Reads the services' events (stream kleinkauf:events, see KleinKaufEventPublisher) and forwards them to the WS.
- No consumer group: each observability instance reads all events, because each one
  has its own open WSs. It starts with the ones arriving since it started (what came before is history and
  the dashboard requests it over REST).
- A Redis error does not stop the reading: it is logged (once a minute) and it keeps trying.
- Store events (tenant.*) go to platform.business; a store's order and stock events (order.*, stock.*) to
  store.activity, only to that store's WSs.
- The audit uses a separate consumer group (AuditConsumer), which does store and acknowledge each event.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "observability.events.enabled", havingValue = "true", matchIfMissing = true)
public class EventStreamListener implements SmartLifecycle {

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration ERROR_LOG_INTERVAL = Duration.ofMinutes(1);
    private static final Set<String> STORE_ACTIVITY = Set.of(KleinKaufEvent.ORDER_CREATED, KleinKaufEvent.ORDER_RECEIPT_UPLOADED,
            KleinKaufEvent.STOCK_MOVEMENT, KleinKaufEvent.STOCK_LOW);

    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private final SessionRegistry sessions;
    private final ObjectMapper mapper;
    private final Clock clock;
    private volatile long lastErrorLog;
    private volatile boolean running;

    public EventStreamListener(RedisConnectionFactory factory, SessionRegistry sessions, ObjectMapper mapper, Clock clock) {
        this.sessions = sessions;
        this.mapper = mapper;
        this.clock = clock;
        this.container = StreamMessageListenerContainer.create(factory,
                StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(POLL_TIMEOUT)
                        .errorHandler(this::logError)
                        .build());
    }

    @Override
    public void start() {
        // An id with the current time: from there on the container continues from the last one it read
        ReadOffset from = ReadOffset.from(clock.millis() + "-0");
        container.register(StreamReadRequest.builder(StreamOffset.create(KleinKaufEvent.STREAM, from))
                .cancelOnError(e -> false)
                .build(), message -> handle(message.getValue()));
        container.start();
        running = true;
    }

    protected void handle(Map<String, String> fields) {
        KleinKaufEvent event = KleinKaufEvent.fromFields(fields, mapper);
        if (event == null) {
            return;
        }
        // Stores (creation, deletion, enabling): the platform dashboard refreshes its summary
        if (event.type().startsWith("tenant.")) {
            sessions.publish(Channels.PLATFORM_BUSINESS, payload(event));
        } else if (STORE_ACTIVITY.contains(event.type()) && !TenantConstants.PUBLIC.equals(event.tenant())) {
            // Orders and stock: only to that store's WSs
            sessions.publishToTenant(Channels.STORE_ACTIVITY, event.tenant(), payload(event));
        }
    }

    // { type, at, ...data }
    private static Map<String, Object> payload(KleinKaufEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", event.type());
        data.put("at", event.at());
        data.putAll(event.data());
        return data;
    }

    private void logError(Throwable error) {
        long now = clock.millis();
        if (now - lastErrorLog >= ERROR_LOG_INTERVAL.toMillis()) {
            lastErrorLog = now;
            log.warn("No se pudieron leer los eventos de Redis (se sigue intentando): {}", error.toString());
        }
    }

    @Override
    public void stop() {
        container.stop();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
