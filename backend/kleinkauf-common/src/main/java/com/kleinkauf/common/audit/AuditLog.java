package com.kleinkauf.common.audit;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

/*
Registro a mano de lo que no es un alta, cambio o baja de una entidad @Audited (docs/auditoria.md):
lecturas sensibles, cambios de estado, importaciones y lo que se escribe con SQL directo.
Va a la outbox con JdbcTemplate: dentro de una transaccion se suma a ella (misma conexion que los cambios);
fuera de una, se escribe en el momento.
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

    // Lectura sensible (ver el costo, exportar, descargar un comprobante)
    public void read(String entity, Object entityId) {
        record(AuditEvent.READ, entity, entityId, Map.of());
    }

    public static AuditEvent.Change change(Object from, Object to) {
        return new AuditEvent.Change(AuditValues.text(from), AuditValues.text(to));
    }
}
