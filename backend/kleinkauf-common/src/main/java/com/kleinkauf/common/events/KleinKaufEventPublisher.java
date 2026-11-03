package com.kleinkauf.common.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.ClassUtils;

import java.time.Clock;
import java.util.Map;

/*
Publica eventos en Redis Streams (KleinKaufEvent.STREAM) para observability.
- Dentro de una transaccion se publica despues del commit: si la operacion se deshace, no hay evento.
- El stream se recorta a unos MAX_LENGTH eventos: es para lo que pasa en vivo, no un historial.
- Si Redis falla se loguea y la operacion sigue: por ahora los eventos son solo para el panel en vivo.
  La auditoria (etapa 3, docs/auditoria.md) no puede perderlos y va a pasar por una tabla outbox.
 */
public class KleinKaufEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KleinKaufEventPublisher.class);

    static final long MAX_LENGTH = 10_000;

    private static final boolean TRANSACTIONS_PRESENT = ClassUtils.isPresent(
            "org.springframework.transaction.support.TransactionSynchronizationManager",
            KleinKaufEventPublisher.class.getClassLoader());

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Clock clock;

    public KleinKaufEventPublisher(StringRedisTemplate redis, ObjectMapper mapper, Clock clock) {
        this.redis = redis;
        this.mapper = mapper;
        this.clock = clock;
    }

    public void publish(String type, String tenant, Map<String, Object> data) {
        KleinKaufEvent event = new KleinKaufEvent(type, tenant, clock.instant(), data);
        if (TRANSACTIONS_PRESENT && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(event);
                }
            });
            return;
        }
        send(event);
    }

    private void send(KleinKaufEvent event) {
        try {
            redis.opsForStream().add(
                    StreamRecords.string(event.toFields(mapper)).withStreamKey(KleinKaufEvent.STREAM),
                    XAddOptions.maxlen(MAX_LENGTH).approximateTrimming(true));
        } catch (RuntimeException e) {
            log.warn("No se pudo publicar el evento {} ({}): {}", event.type(), event.tenant(), e.toString());
        }
    }
}
