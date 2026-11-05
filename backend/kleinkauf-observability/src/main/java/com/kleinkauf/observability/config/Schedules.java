package com.kleinkauf.observability.config;

import com.kleinkauf.observability.collector.Collector;
import com.kleinkauf.observability.ws.AccessRevalidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/*
Periodic tasks. observability.scheduling.enabled=false turns them off (tests call them manually).
An error in one round is logged and the next one runs anyway.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "observability.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class Schedules {

    private final Collector collector;
    private final AccessRevalidator accessRevalidator;

    public Schedules(Collector collector, AccessRevalidator accessRevalidator) {
        this.collector = collector;
        this.accessRevalidator = accessRevalidator;
    }

    @Scheduled(fixedDelayString = "${observability.poll.interval:10s}", initialDelayString = "5s")
    public void collect() {
        try {
            collector.collect();
        } catch (RuntimeException e) {
            log.warn("Fallo una vuelta del recolector: {}", e.toString());
        }
    }

    @Scheduled(fixedDelayString = "${observability.ws.revalidate-interval:60s}",
            initialDelayString = "${observability.ws.revalidate-interval:60s}")
    public void revalidate() {
        try {
            accessRevalidator.revalidate();
        } catch (RuntimeException e) {
            log.warn("Fallo la revision de los accesos de los WS: {}", e.toString());
        }
    }
}
