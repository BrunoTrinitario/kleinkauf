package com.kleinkauf.observability.audit;

import com.kleinkauf.common.audit.AuditEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;

/*
The audit history as CSV (GET .../audit/export), to open in Excel or Google Sheets:
- UTF-8 with BOM (otherwise Excel breaks the accents) and one row per event, newest to oldest.
- The date in audit.export.zone (Argentina by default), without a zone: it is the time the person who made the change saw.
- cambios (changes) column: "campo: antes -> despues", separated by "; ".
- Texts starting with = + - @ get a leading ': otherwise Excel takes them as a formula (a product name
  like "=HYPERLINK(...)" would run when the file is opened).
 */
@Component
public class AuditCsv {

    static final String HEADER = "fecha,usuario,id_usuario,rol,accion,entidad,id_entidad,cambios,servicio,ip";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ZoneId zone;

    public AuditCsv(@Value("${audit.export.zone:America/Argentina/Buenos_Aires}") String zone) {
        this.zone = ZoneId.of(zone);
    }

    public StringBuilder start() {
        return new StringBuilder("﻿").append(HEADER).append("\r\n");
    }

    public void append(StringBuilder csv, AuditEvent event) {
        AuditEvent.Actor actor = event.actor();
        csv.append(DATE.format(event.at().atZone(zone))).append(',')
                .append(text(actor == null ? null : actor.name())).append(',')
                .append(actor == null || actor.userId() == null ? "" : actor.userId()).append(',')
                .append(text(actor == null ? null : actor.role())).append(',')
                .append(text(event.action())).append(',')
                .append(text(event.entity())).append(',')
                .append(text(event.entityId())).append(',')
                .append(text(changes(event.changes()))).append(',')
                .append(text(event.service())).append(',')
                .append(text(event.ip())).append("\r\n");
    }

    public static byte[] bytes(StringBuilder csv) {
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String changes(Map<String, AuditEvent.Change> changes) {
        if (changes == null || changes.isEmpty()) {
            return null;
        }
        return changes.entrySet().stream()
                .map(e -> e.getKey() + ": " + value(e.getValue() == null ? null : e.getValue().from())
                        + " -> " + value(e.getValue() == null ? null : e.getValue().to()))
                .collect(Collectors.joining("; "));
    }

    private static String value(Object value) {
        return value == null ? "(vacio)" : String.valueOf(value);
    }

    // Quoted if needed; quotes are doubled
    static String text(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String safe = "=+-@".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }
}
