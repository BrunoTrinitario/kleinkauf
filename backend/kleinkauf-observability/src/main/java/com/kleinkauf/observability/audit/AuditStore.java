package com.kleinkauf.observability.audit;

import com.kleinkauf.common.audit.AuditEvent;
import com.kleinkauf.observability.dto.PageDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/*
La auditoria guardada (docs/auditoria.md): <schema>.audit_events en la base observability, una tabla por
tienda y la de public para la plataforma. Se guarda para siempre y nadie la edita ni la borra.
- El schema y la tabla se crean con el primer evento de cada tienda.
- Un evento repetido (la outbox puede mandarlo dos veces) no se guarda de nuevo: ON CONFLICT con el id,
  que es la unica clave unica.
- changes va como texto JSON: no se filtra por su contenido.
Los nombres de schema se validan antes de meterlos en el SQL (no se pueden pasar como parametro).
 */
@Component
public class AuditStore {

    public static final int MAX_PAGE_SIZE = 100;

    private static final Pattern SCHEMA = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");
    private static final TypeReference<Map<String, AuditEvent.Change>> CHANGES = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Set<String> ready = ConcurrentHashMap.newKeySet();

    public AuditStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    // Filtros de las consultas; los null no filtran
    public record Query(Instant from, Instant to, Long userId, String entity, String entityId, String action) {
    }

    public static boolean validSchema(String schema) {
        return schema != null && SCHEMA.matcher(schema).matches();
    }

    // true si se guardo; false si ya estaba
    public boolean insert(AuditEvent event) throws Exception {
        String table = table(event.tenant());
        AuditEvent.Actor actor = event.actor();
        int rows = jdbc.update("INSERT INTO " + table + " (id, occurred_at, service, actor_user_id, actor_name, actor_role, ip, "
                        + "trace_id, action, entity, entity_id, changes, received_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                event.id(), Timestamp.from(event.at()), event.service(),
                actor == null ? null : actor.userId(), actor == null ? null : actor.name(), actor == null ? null : actor.role(),
                event.ip(), event.traceId(), event.action(), event.entity(), event.entityId(),
                mapper.writeValueAsString(event.changes()), Timestamp.from(Instant.now()));
        return rows > 0;
    }

    public PageDto<AuditEvent> page(String schema, Query query, int page, int size) {
        String table = table(schema);
        int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int pageNumber = Math.max(0, page);

        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (query.from() != null) { where.append(" AND occurred_at >= ?"); args.add(Timestamp.from(query.from())); }
        if (query.to() != null) { where.append(" AND occurred_at < ?"); args.add(Timestamp.from(query.to())); }
        if (query.userId() != null) { where.append(" AND actor_user_id = ?"); args.add(query.userId()); }
        if (query.entity() != null) { where.append(" AND entity = ?"); args.add(query.entity()); }
        if (query.entityId() != null) { where.append(" AND entity_id = ?"); args.add(query.entityId()); }
        if (query.action() != null) { where.append(" AND action = ?"); args.add(query.action()); }

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((long) pageNumber * pageSize);
        List<AuditEvent> items = jdbc.query("SELECT * FROM " + table + where
                + " ORDER BY occurred_at DESC, id LIMIT ? OFFSET ?", rowMapper(schema), pageArgs.toArray());
        long totalItems = total == null ? 0 : total;
        return new PageDto<>(items, pageNumber, pageSize, totalItems, (int) ((totalItems + pageSize - 1) / pageSize));
    }

    private RowMapper<AuditEvent> rowMapper(String schema) {
        return (rs, i) -> {
            long userId = rs.getLong("actor_user_id");
            AuditEvent.Actor actor = rs.wasNull() ? null
                    : new AuditEvent.Actor(userId, rs.getString("actor_name"), rs.getString("actor_role"));
            Map<String, AuditEvent.Change> changes;
            try {
                String json = rs.getString("changes");
                changes = json == null ? Map.of() : mapper.readValue(json, CHANGES);
            } catch (Exception e) {
                changes = Map.of();
            }
            return new AuditEvent(rs.getString("id"), schema, rs.getTimestamp("occurred_at").toInstant(), rs.getString("service"),
                    actor, rs.getString("ip"), rs.getString("trace_id"), rs.getString("action"),
                    rs.getString("entity"), rs.getString("entity_id"), changes);
        };
    }

    private String table(String schema) {
        if (!validSchema(schema)) {
            throw new IllegalArgumentException("Schema invalido: " + schema);
        }
        if (ready.add(schema)) {
            try {
                jdbc.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
                jdbc.execute("CREATE TABLE IF NOT EXISTS " + schema + ".audit_events ("
                        + "id VARCHAR(36) PRIMARY KEY, "
                        + "occurred_at TIMESTAMP WITH TIME ZONE NOT NULL, "
                        + "service VARCHAR(40), "
                        + "actor_user_id BIGINT, "
                        + "actor_name VARCHAR(200), "
                        + "actor_role VARCHAR(40), "
                        + "ip VARCHAR(64), "
                        + "trace_id VARCHAR(64), "
                        + "action VARCHAR(40) NOT NULL, "
                        + "entity VARCHAR(60) NOT NULL, "
                        + "entity_id VARCHAR(100), "
                        + "changes TEXT, "
                        + "received_at TIMESTAMP WITH TIME ZONE NOT NULL)");
                // Las consultas: lo ultimo, por usuario y por una cosa puntual
                jdbc.execute("CREATE INDEX IF NOT EXISTS audit_events_at ON " + schema + ".audit_events (occurred_at)");
                jdbc.execute("CREATE INDEX IF NOT EXISTS audit_events_actor ON " + schema + ".audit_events (actor_user_id, occurred_at)");
                jdbc.execute("CREATE INDEX IF NOT EXISTS audit_events_entity ON " + schema + ".audit_events (entity, entity_id, occurred_at)");
            } catch (RuntimeException e) {
                ready.remove(schema);
                throw e;
            }
        }
        return schema + ".audit_events";
    }
}
