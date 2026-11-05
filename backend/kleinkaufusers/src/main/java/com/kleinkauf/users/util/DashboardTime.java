package com.kleinkauf.users.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/*
Dashboard days (store and user sign-ups). Dates are stored with LocalDateTime.now() in the server's
zone (UTC in Docker), but a "day" is in users.dashboard.zone (Argentina by default): a store created on Monday
at 10 pm in Argentina belongs to Monday, even though in UTC it is already Tuesday. Like SalesTime in sales.
 */
@Component
public class DashboardTime {

    private final ZoneId zone;
    private final ZoneId serverZone;

    @Autowired
    public DashboardTime(@Value("${users.dashboard.zone:America/Argentina/Buenos_Aires}") String zone) {
        this(ZoneId.of(zone), ZoneId.systemDefault());
    }

    // For tests: fixed server zone
    public DashboardTime(ZoneId zone, ZoneId serverZone) {
        this.zone = zone;
        this.serverZone = serverZone;
    }

    public LocalDate today() {
        return LocalDate.now(zone);
    }

    // Start of the day in server time (to compare with what is stored)
    public LocalDateTime startOfDay(LocalDate day) {
        return day.atStartOfDay(zone).withZoneSameInstant(serverZone).toLocalDateTime();
    }

    // Day of a stored date
    public LocalDate dayOf(LocalDateTime stored) {
        return stored.atZone(serverZone).withZoneSameInstant(zone).toLocalDate();
    }
}
