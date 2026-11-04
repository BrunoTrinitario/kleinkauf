package com.kleinkauf.observability.controllers;

import com.kleinkauf.observability.audit.AuditCsv;
import com.kleinkauf.observability.audit.AuditStore;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
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

    /*
    El historial filtrado en CSV, como descarga (auditoria-<nombre>-<fecha>.csv). Con mas de MAX_EXPORT_ROWS eventos
    responde 400 en vez de cortarlo: un archivo incompleto parece completo
     */
    static ResponseEntity<byte[]> export(AuditStore audit, AuditCsv csv, String schema, AuditStore.Query query, String name) {
        if (audit.count(schema, query) > AuditStore.MAX_EXPORT_ROWS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Hay mas de " + AuditStore.MAX_EXPORT_ROWS + " eventos: acota las fechas");
        }
        StringBuilder out = csv.start();
        audit.forEach(schema, query, AuditStore.MAX_EXPORT_ROWS, event -> csv.append(out, event));
        String filename = "auditoria-" + name + "-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(AuditCsv.bytes(out));
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
