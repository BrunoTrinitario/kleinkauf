package com.kleinkauf.observability.controllers;

import com.kleinkauf.observability.audit.AuditStore;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/*
Filtros de las consultas de auditoria. from y to son fechas con zona (ISO-8601, ej. 2026-11-02T03:00:00Z):
el frontend sabe que es "un dia" para el que mira. to no se incluye.
 */
final class AuditQueries {

    static final int DEFAULT_SIZE = 20;

    private AuditQueries() {
    }

    static AuditStore.Query query(String from, String to, Long userId, String entity, String entityId, String action) {
        return new AuditStore.Query(instant(from, "from"), instant(to, "to"), userId, blankToNull(entity),
                blankToNull(entityId), action == null || action.isBlank() ? null : action.trim().toUpperCase(Locale.ROOT));
    }

    private static Instant instant(String value, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value.trim()).toInstant();
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " tiene que ser una fecha ISO-8601 con zona");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
