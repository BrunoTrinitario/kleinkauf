package com.kleinkauf.common.audit;

import java.time.Instant;
import java.util.Map;

/*
An audit record (format in docs/auditoria.md). Travels as JSON through the outbox and Redis Streams
(stream kleinkauf:audit) to observability, which stores it.
- tenant: the store's schema, or "public" for the platform.
- actor: null if there is no logged-in user (self-service store sign-up). Observability fills in the name.
- changes: only the fields that changed (on a create, the ones that have a value).
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

    public static final String STREAM = "kleinkauf:audit";

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
