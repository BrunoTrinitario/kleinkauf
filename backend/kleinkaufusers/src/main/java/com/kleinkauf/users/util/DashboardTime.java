package com.kleinkauf.users.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/*
Dias de los paneles (altas de tiendas y de usuarios). Las fechas se guardan con LocalDateTime.now() en la zona del
servidor (UTC en Docker), pero un "dia" es en users.dashboard.zone (Argentina por defecto): una tienda creada el lunes
a las 22 hs en Argentina es del lunes, aunque en UTC ya sea martes. Como SalesTime en sales.
 */
@Component
public class DashboardTime {

    private final ZoneId zone;
    private final ZoneId serverZone;

    @Autowired
    public DashboardTime(@Value("${users.dashboard.zone:America/Argentina/Buenos_Aires}") String zone) {
        this(ZoneId.of(zone), ZoneId.systemDefault());
    }

    // Para tests: la zona del servidor fija
    public DashboardTime(ZoneId zone, ZoneId serverZone) {
        this.zone = zone;
        this.serverZone = serverZone;
    }

    public LocalDate today() {
        return LocalDate.now(zone);
    }

    // Inicio del dia en la hora del servidor (para comparar con lo guardado)
    public LocalDateTime startOfDay(LocalDate day) {
        return day.atStartOfDay(zone).withZoneSameInstant(serverZone).toLocalDateTime();
    }

    // Dia de una fecha guardada
    public LocalDate dayOf(LocalDateTime stored) {
        return stored.atZone(serverZone).withZoneSameInstant(zone).toLocalDate();
    }
}
