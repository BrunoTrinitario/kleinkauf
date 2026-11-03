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
Lee los eventos de los servicios (stream kleinkauf:events, ver KleinKaufEventPublisher) y los reenvia al WS.
- Sin grupo de consumidores: cada instancia de observability lee todos los eventos, porque cada una
  tiene sus propios WS abiertos. Empieza por los que llegan desde que arranco (lo anterior es historia y
  el panel lo pide por REST).
- Un error de Redis no corta la lectura: se loguea (una vez por minuto) y se sigue intentando.
- Los de tiendas (tenant.*) van a platform.business; los de pedidos y stock de una tienda (order.*, stock.*) a
  store.activity, solo a los WS de esa tienda.
- La auditoria usa un grupo de consumidores aparte (AuditConsumer), que si guarda y confirma cada evento.
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
        // Un id con la hora de ahora: de ahi en adelante el contenedor sigue desde el ultimo que leyo
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
        // Las tiendas (altas, bajas, habilitar): el panel de la plataforma refresca su resumen
        if (event.type().startsWith("tenant.")) {
            sessions.publish(Channels.PLATFORM_BUSINESS, payload(event));
        } else if (STORE_ACTIVITY.contains(event.type()) && !TenantConstants.PUBLIC.equals(event.tenant())) {
            // Pedidos y stock: solo a los WS de esa tienda
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
