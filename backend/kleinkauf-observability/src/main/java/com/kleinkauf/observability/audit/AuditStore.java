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
La auditoria guardada (docs/auditoria.md): <schema>.audit_events en la base observability, una tabla por
tienda y la de public para la plataforma. Se guarda para siempre y nadie la edita ni la borra.
- Particionada por mes (occurred_at, en UTC): audit_events_2026_11, etc. Las consultas por fecha leen solo los meses
  del rango, y un mes viejo se puede archivar o mover sin tocar el resto. La particion del mes se crea antes de
  guardar el primer evento de ese mes.
- El schema y la tabla se crean con el primer evento de cada tienda. Una tabla de antes (sin particiones) se pasa a
  particionada la primera vez que se usa, en una transaccion.
- Un evento repetido (la outbox puede mandarlo dos veces) no se guarda de nuevo: ON CONFLICT con (id, occurred_at),
  la clave unica (en una tabla particionada tiene que incluir la columna de la particion; el repetido trae la misma
  fecha).
- changes va como texto JSON: no se filtra por su contenido.
Los nombres de schema se validan antes de meterlos en el SQL (no se pueden pasar como parametro).
 */
@Slf4j
@Component
public class AuditStore {

    public static final int MAX_PAGE_SIZE = 100;
    // Tope de la exportacion a CSV: con mas, hay que acotar las fechas
    public static final int MAX_EXPORT_ROWS = 50_000;

    private static final Pattern SCHEMA = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");
    private static final TypeReference<Map<String, AuditEvent.Change>> CHANGES = new TypeReference<>() {
    };
    private static final String COLUMNS = "id, occurred_at, service, actor_user_id, actor_name, actor_role, ip, "
            + "trace_id, action, entity, entity_id, changes, received_at";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Set<String> ready = ConcurrentHashMap.newKeySet();
    // <schema>:<mes> de las particiones que ya se crearon
    private final Set<String> partitions = ConcurrentHashMap.newKeySet();

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

    // Todos los eventos que cumplen los filtros (para exportar), de lo mas nuevo a lo mas viejo, hasta limit
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

    // Crea la tabla particionada (o pasa la vieja a particionada) con sus indices y la particion del mes actual
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
                // Se borra despues de copiar: sus indices tienen los nombres que usa la tabla nueva
                st.execute("DROP TABLE " + schema + ".audit_events_legacy");
                log.info("Auditoria de {}: {} eventos pasados a la tabla particionada ({} meses)", schema, moved, months.size());
            }
            // Las consultas: lo ultimo, por usuario y por una cosa puntual. Se crean en cada particion
            st.execute("CREATE INDEX IF NOT EXISTS audit_events_at ON " + schema + ".audit_events (occurred_at)");
            st.execute("CREATE INDEX IF NOT EXISTS audit_events_actor ON " + schema + ".audit_events (actor_user_id, occurred_at)");
            st.execute("CREATE INDEX IF NOT EXISTS audit_events_entity ON " + schema + ".audit_events (entity, entity_id, occurred_at)");
        }
    }

    // r: tabla comun (la de antes), p: particionada, null: no existe
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

    // audit_events_2026_11: de 2026-11-01 a 2026-12-01 en UTC. IF NOT EXISTS: otra instancia pudo haberla creado
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
