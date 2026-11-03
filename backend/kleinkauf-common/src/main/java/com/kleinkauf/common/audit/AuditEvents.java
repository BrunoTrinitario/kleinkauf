package com.kleinkauf.common.audit;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/*
Arma los eventos con lo que se sabe de la request en curso (AuditRequest). null si no corresponde auditar
(llamada interna o sin request).
 */
public class AuditEvents {

    private final String service;
    private final Clock clock;

    public AuditEvents(String service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    public AuditEvent build(String action, String entity, String entityId, Map<String, AuditEvent.Change> changes) {
        AuditRequest.Info info = AuditRequest.current();
        if (info == null) {
            return null;
        }
        return new AuditEvent(UUID.randomUUID().toString(), info.tenant(), clock.instant(), service, info.actor(),
                info.ip(), info.traceId(), action, entity, entityId, changes);
    }

    // "products-microservice" -> "products"
    public static String serviceName(String applicationName) {
        if (applicationName == null || applicationName.isBlank()) {
            return "unknown";
        }
        return applicationName.replace("-microservice", "");
    }
}
