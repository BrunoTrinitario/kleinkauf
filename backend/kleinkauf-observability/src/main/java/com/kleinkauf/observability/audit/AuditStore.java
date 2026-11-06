package com.kleinkauf.observability.audit;

import com.kleinkauf.common.audit.AuditEvent;
import com.kleinkauf.observability.dto.PageDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/*
The stored audit (docs/features/audit.md): <schema>.audit_events in the observability database, one table per
store plus public's for the platform. Kept forever and nobody edits or deletes it.
- Partitioned by month (occurred_at, in UTC): audit_events_2026_11, etc. Date queries only read the months
  in the range, and an old month can be archived or moved without touching the rest. The month's partition is created before
  storing the first event of that month.
- The schema and table are created with each store's first event. An older table (without partitions) is converted to
  partitioned the first time it is used, in a transaction.
- A repeated event (the outbox may send it twice) is not stored again: ON CONFLICT on (id, occurred_at),
  the unique key (in a partitioned table it must include the partition column; the repeated one carries the same
  date).
- changes goes as JSON text: it is not filtered by its content.
Schema names are validated before putting them in the SQL (they cannot be passed as a parameter).
 */
@Slf4j
@Component
public class AuditStore {

    public static final int MAX_PAGE_SIZE = 100;
    // Cap of the CSV export: with more, the dates have to be narrowed
    public static final int MAX_EXPORT_ROWS = 50_000;

    private static final Pattern SCHEMA = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");
    private static final TypeReference<Map<String, AuditEvent.Change>> CHANGES = new TypeReference<>() {
    };
    private static final String COLUMNS = "id, occurred_at, service, actor_user_id, actor_name, actor_role, ip, "
            + "trace_id, action, entity, entity_id, changes, received_at";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Set<String> ready = ConcurrentHashMap.newKeySet();
    // <schema>:<month> of the partitions already created
    private final Set<String> partitions = ConcurrentHashMap.newKeySet();

    public AuditStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    // Query filters; nulls do not filter
    public record Query(Instant from, Instant to, Long userId, String entity, String entityId, String action) {
    }

    public static boolean validSchema(String schema) {
        return schema != null && SCHEMA.matcher(schema).matches();
    }

    // true if it was stored; false if it was already there
    public boolean insert(AuditEvent event) throws Exception {
        String table = table(event.tenant());
        partition(event.tenant(), YearMonth.from(event.at().atZone(ZoneOffset.UTC)));
        AuditEvent.Actor actor = event.actor();
        int rows = jdbc.update("INSERT INTO " + table + " (" + COLUMNS + ") "
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

        List<Object> args = new ArrayList<>();
        String where = where(query, args);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((long) pageNumber * pageSize);
        List<AuditEvent> items = jdbc.query("SELECT * FROM " + table + where
                + " ORDER BY occurred_at DESC, id LIMIT ? OFFSET ?", rowMapper(schema), pageArgs.toArray());
        long totalItems = total == null ? 0 : total;
        return new PageDto<>(items, pageNumber, pageSize, totalItems, (int) ((totalItems + pageSize - 1) / pageSize));
    }

    public long count(String schema, Query query) {
        List<Object> args = new ArrayList<>();
        String where = where(query, args);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + table(schema) + where, Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    // All events matching the filters (for export), newest to oldest, up to limit
    public void forEach(String schema, Query query, int limit, Consumer<AuditEvent> each) {
        String table = table(schema);
        List<Object> args = new ArrayList<>();
        String where = where(query, args);
        args.add(limit);
        RowMapper<AuditEvent> rows = rowMapper(schema);
        RowCallbackHandler handler = rs -> each.accept(rows.mapRow(rs, rs.getRow()));
        jdbc.query("SELECT * FROM " + table + where + " ORDER BY occurred_at DESC, id LIMIT ?", handler, args.toArray());
    }

    private static String where(Query query, List<Object> args) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (query.from() != null) { where.append(" AND occurred_at >= ?"); args.add(Timestamp.from(query.from())); }
        if (query.to() != null) { where.append(" AND occurred_at < ?"); args.add(Timestamp.from(query.to())); }
        if (query.userId() != null) { where.append(" AND actor_user_id = ?"); args.add(query.userId()); }
        if (query.entity() != null) { where.append(" AND entity = ?"); args.add(query.entity()); }
        if (query.entityId() != null) { where.append(" AND entity_id = ?"); args.add(query.entityId()); }
        if (query.action() != null) { where.append(" AND action = ?"); args.add(query.action()); }
        return where.toString();
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
                jdbc.execute((ConnectionCallback<Void>) con -> {
                    inTransaction(con, () -> prepare(con, schema));
                    return null;
                });
            } catch (RuntimeException e) {
                ready.remove(schema);
                throw e;
            }
        }
        return schema + ".audit_events";
    }

    // Creates the partitioned table (or converts the old one to partitioned) with its indexes and the current month's partition
    private void prepare(Connection con, String schema) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
            String kind = relkind(con, schema);
            boolean legacy = "r".equals(kind);
            if (legacy) {
                st.execute("ALTER TABLE " + schema + ".audit_events RENAME TO audit_events_legacy");
            }
            if (!"p".equals(kind)) {
                st.execute("CREATE TABLE " + schema + ".audit_events ("
                        + "id VARCHAR(36) NOT NULL, "
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
                        + "received_at TIMESTAMP WITH TIME ZONE NOT NULL, "
                        + "PRIMARY KEY (id, occurred_at)"
                        + ") PARTITION BY RANGE (occurred_at)");
            }
            if (legacy) {
                List<YearMonth> months = new ArrayList<>();
                try (ResultSet rs = st.executeQuery("SELECT DISTINCT to_char(occurred_at AT TIME ZONE 'UTC', 'YYYY-MM') "
                        + "FROM " + schema + ".audit_events_legacy")) {
                    while (rs.next()) {
                        months.add(YearMonth.parse(rs.getString(1)));
                    }
                }
                for (YearMonth month : months) {
                    st.execute(partitionDdl(schema, month));
                }
                int moved = st.executeUpdate("INSERT INTO " + schema + ".audit_events (" + COLUMNS + ") SELECT " + COLUMNS
                        + " FROM " + schema + ".audit_events_legacy");
                // Dropped after copying: its indexes have the names the new table uses
                st.execute("DROP TABLE " + schema + ".audit_events_legacy");
                log.info("Auditoria de {}: {} eventos pasados a la tabla particionada ({} meses)", schema, moved, months.size());
            }
            // The queries: latest, by user and by one specific thing. Created on each partition
            st.execute("CREATE INDEX IF NOT EXISTS audit_events_at ON " + schema + ".audit_events (occurred_at)");
            st.execute("CREATE INDEX IF NOT EXISTS audit_events_actor ON " + schema + ".audit_events (actor_user_id, occurred_at)");
            st.execute("CREATE INDEX IF NOT EXISTS audit_events_entity ON " + schema + ".audit_events (entity, entity_id, occurred_at)");
        }
    }

    // r: plain table (the old one), p: partitioned, null: does not exist
    private static String relkind(Connection con, String schema) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT c.relkind FROM pg_class c "
                + "JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = ? AND c.relname = 'audit_events'")) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private void partition(String schema, YearMonth month) {
        String key = schema + ":" + month;
        if (partitions.contains(key)) {
            return;
        }
        jdbc.execute(partitionDdl(schema, month));
        partitions.add(key);
    }

    // audit_events_2026_11: from 2026-11-01 to 2026-12-01 in UTC. IF NOT EXISTS: another instance may have created it
    static String partitionDdl(String schema, YearMonth month) {
        return "CREATE TABLE IF NOT EXISTS " + schema + ".audit_events_" + month.getYear() + "_"
                + String.format("%02d", month.getMonthValue()) + " PARTITION OF " + schema + ".audit_events"
                + " FOR VALUES FROM ('" + month.atDay(1) + " 00:00:00+00') TO ('" + month.plusMonths(1).atDay(1) + " 00:00:00+00')";
    }

    private interface SqlWork {
        void run() throws SQLException;
    }

    private static void inTransaction(Connection con, SqlWork work) throws SQLException {
        boolean autoCommit = con.getAutoCommit();
        con.setAutoCommit(false);
        try {
            work.run();
            con.commit();
        } catch (SQLException | RuntimeException e) {
            con.rollback();
            throw e;
        } finally {
            con.setAutoCommit(autoCommit);
        }
    }
}
