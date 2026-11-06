package com.kleinkauf.common.audit;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

/*
Manual recording of what is not a create, update or delete of an @Audited entity (docs/features/audit.md):
sensitive reads, status changes, imports and whatever is written with plain SQL.
Goes to the outbox with JdbcTemplate: inside a transaction it joins it (same connection as the changes);
outside one, it is written right away.
 */
public class AuditLog {

    private final AuditEvents events;
    private final OutboxStore outbox;
    private final JdbcTemplate jdbc;

    public AuditLog(AuditEvents events, OutboxStore outbox, JdbcTemplate jdbc) {
        this.events = events;
        this.outbox = outbox;
        this.jdbc = jdbc;
    }

    public void record(String action, String entity, Object entityId, Map<String, AuditEvent.Change> changes) {
        AuditEvent event = events.build(action, entity, entityId == null ? null : String.valueOf(entityId), changes);
        if (event != null) {
            outbox.insert(jdbc, event);
        }
    }

    // Sensitive read (seeing the cost, exporting, downloading a receipt)
    public void read(String entity, Object entityId) {
        record(AuditEvent.READ, entity, entityId, Map.of());
    }

    public static AuditEvent.Change change(Object from, Object to) {
        return new AuditEvent.Change(AuditValues.text(from), AuditValues.text(to));
    }
}
