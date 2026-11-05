package com.kleinkauf.sales.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/*
Store dates. Orders are stored with LocalDateTime.now() in the server's zone
(UTC with bootRun), but a "day" for the store is in its zone (sales.reports.zone, Argentina by default):
a sale on Monday at 10 pm in Argentina belongs to Monday, even though in UTC it is already Tuesday.
Used by the reports and the date filters of the order list.
 */
@Component
public class SalesTime {

    private final ZoneId zone;
    private final ZoneId serverZone = ZoneId.systemDefault();

    public SalesTime(@Value("${sales.reports.zone:America/Argentina/Buenos_Aires}") String zone) {
        this.zone = ZoneId.of(zone);
    }

    public ZoneId zone() {
        return zone;
    }

    public LocalDate today() {
        return LocalDate.now(zone);
    }

    // Start of the store day, in server time (to compare with what is stored)
    public LocalDateTime startOfDay(LocalDate day) {
        return day.atStartOfDay(zone).withZoneSameInstant(serverZone).toLocalDateTime();
    }

    // Store day of a stored date
    public LocalDate dayOf(LocalDateTime stored) {
        return stored.atZone(serverZone).withZoneSameInstant(zone).toLocalDate();
    }
}
