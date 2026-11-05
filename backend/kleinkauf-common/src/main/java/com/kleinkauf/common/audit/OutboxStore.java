package com.kleinkauf.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

/*
Audit outbox table: public.outbox_events, a single one per service with a tenant column.
It lives in public (and not in each store's schema) so the publisher reads a single table; it is written with the
same connection as the change, so it stays in the same transaction (in Postgres schemas share the database).
Plain SQL (no partial indexes or jsonb) so it also works on H2 in the tests.
 */
public class OutboxStore {

    static final String TABLE = "public.outbox_events";

    static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
            + "id VARCHAR(36) PRIMARY KEY, "
            + "tenant VARCHAR(63) NOT NULL, "
            + "created_at TIMESTAMP WITH TIME ZONE NOT NULL, "
            + "payload TEXT NOT NULL, "
            + "sent_at TIMESTAMP WITH TIME ZONE)";
    static final String CREATE_INDEX = "CREATE INDEX IF NOT EXISTS outbox_events_pending ON " + TABLE + " (sent_at, created_at)";

    private static final String INSERT = "INSERT INTO " + TABLE + " (id, tenant, created_at, payload) VALUES (?, ?, ?, ?)";

    private final ObjectMapper mapper;

    public OutboxStore(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void createTable(JdbcTemplate jdbc) {
        jdbc.execute(CREATE_TABLE);
        jdbc.execute(CREATE_INDEX);
    }

    // With the Hibernate session's connection (listener): same transaction as the change
    public void insert(Connection connection, AuditEvent event) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, event.id());
            statement.setString(2, event.tenant());
            statement.setTimestamp(3, Timestamp.from(event.at()));
            statement.setString(4, json(event));
            statement.executeUpdate();
        }
    }

    // With JdbcTemplate (AuditLog): joins the current transaction if there is one
    public void insert(JdbcTemplate jdbc, AuditEvent event) {
        jdbc.update(INSERT, event.id(), event.tenant(), Timestamp.from(event.at()), json(event));
    }

    String json(AuditEvent event) {
        try {
            return mapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Evento de auditoria no serializable", e);
        }
    }
}
