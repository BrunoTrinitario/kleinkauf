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
Publishes events to Redis Streams (KleinKaufEvent.STREAM) for observability.
- Inside a transaction it publishes after the commit: if the operation is rolled back, there is no event.
- The stream is trimmed to about MAX_LENGTH events: it is for what happens live, not a history.
- If Redis fails it is logged and the operation goes on: for now events are only for the live dashboard.
  The audit (stage 3, docs/features/audit.md) cannot lose them and goes through an outbox table.
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
