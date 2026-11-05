package com.kleinkauf.observability;

import com.kleinkauf.common.audit.AuditEvent;
import com.kleinkauf.observability.audit.AuditCsv;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// The audit CSV: opens fine in Excel and does not run formulas
class AuditCsvTest {

    private final AuditCsv csv = new AuditCsv("America/Argentina/Buenos_Aires");

    @Test
    void oneRowPerEventWithArgentineTimeAndTheChanges() {
        Map<String, AuditEvent.Change> changes = new LinkedHashMap<>();
        changes.put("price", new AuditEvent.Change("15000.00", "18000.00"));
        changes.put("description", new AuditEvent.Change(null, "Nueva"));
        StringBuilder out = csv.start();

        csv.append(out, new AuditEvent("e1", "mi_tienda", Instant.parse("2026-11-02T01:30:00Z"), "products",
                new AuditEvent.Actor(3L, "Ana", "ADMINISTRADOR"), "1.2.3.4", null, "UPDATE", "Product", "12", changes));
        // No user (a self-service sign-up)
        csv.append(out, new AuditEvent("e2", "public", Instant.parse("2026-11-02T15:00:00Z"), "users",
                null, null, null, "CREATE", "Tenant", "7", Map.of()));

        String text = new String(AuditCsv.bytes(out), StandardCharsets.UTF_8);
        String[] lines = text.split("\r\n");
        assertThat(text).startsWith("﻿fecha,usuario,");
        assertThat(lines[1]).isEqualTo("2026-11-01 22:30:00,Ana,3,ADMINISTRADOR,UPDATE,Product,12,"
                + "price: 15000.00 -> 18000.00; description: (vacio) -> Nueva,products,1.2.3.4");
        assertThat(lines[2]).isEqualTo("2026-11-02 12:00:00,,,,CREATE,Tenant,7,,users,");
    }

    @Test
    void quotesCommasAndFormulas() {
        StringBuilder out = new StringBuilder();

        csv.append(out, new AuditEvent("e1", "mi_tienda", Instant.parse("2026-11-02T15:00:00Z"), "products",
                new AuditEvent.Actor(3L, "=HYPERLINK(\"x\")", "ADMINISTRADOR"), null, null, "UPDATE", "Product", "1",
                Map.of("name", new AuditEvent.Change("Mate, grande", "Mate \"premium\""))));

        assertThat(out.toString()).contains(",\"'=HYPERLINK(\"\"x\"\")\",3,")
                .contains(",\"name: Mate, grande -> Mate \"\"premium\"\"\",");
    }
}
