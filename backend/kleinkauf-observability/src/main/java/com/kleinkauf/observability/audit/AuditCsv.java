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
El historial de auditoria en CSV (GET .../audit/export), para abrirlo en Excel o Google Sheets:
- UTF-8 con BOM (si no, Excel rompe los acentos) y una fila por evento, de lo mas nuevo a lo mas viejo.
- La fecha en audit.export.zone (Argentina por defecto), sin zona: es la hora que vio el que hizo el cambio.
- cambios: "campo: antes -> despues", separados por "; ".
- Los textos que empiezan con = + - @ llevan un ' adelante: si no, Excel los toma como formula (un nombre de producto
  como "=HYPERLINK(...)" se ejecutaria al abrir el archivo).
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

    // Entre comillas si hace falta; las comillas se duplican
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
