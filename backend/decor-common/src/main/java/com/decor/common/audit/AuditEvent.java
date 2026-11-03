package com.decor.common.audit;

import java.time.Instant;
import java.util.Map;

/*
Un registro de auditoria (formato en docs/auditoria.md). Viaja como JSON por la outbox y Redis Streams
(stream decor:audit) hasta observability, que lo guarda.
- tenant: el schema de la tienda, o "public" para la plataforma.
- actor: null si no hay usuario logueado (alta de tienda por autoservicio). El nombre lo completa observability.
- changes: solo los campos que cambiaron (en un alta, los que tienen valor).
 */
public record AuditEvent(
        String id,
        String tenant,
        Instant at,
        String service,
        Actor actor,
        String ip,
        String traceId,
        String action,
        String entity,
        String entityId,
        Map<String, Change> changes
) {

    public static final String STREAM = "decor:audit";

    public static final String CREATE = "CREATE";
    public static final String UPDATE = "UPDATE";
    public static final String DELETE = "DELETE";
    public static final String READ = "READ";
    public static final String STATUS_CHANGE = "STATUS_CHANGE";
    public static final String IMPORT = "IMPORT";
    public static final String ACTIVATE = "ACTIVATE";
    public static final String DEACTIVATE = "DEACTIVATE";

    public record Actor(Long userId, String name, String role) {
    }

    public record Change(String from, String to) {
    }

    public AuditEvent {
        changes = changes == null ? Map.of() : changes;
    }
}
