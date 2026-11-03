package com.kleinkauf.sales.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/*
Fechas de la tienda. Los pedidos se guardan con LocalDateTime.now() en la zona del servidor
(UTC con bootRun), pero un "dia" para la tienda es en su zona (sales.reports.zone, Argentina por defecto):
una venta del lunes a las 22 hs en Argentina es del lunes, aunque en UTC ya sea martes.
Lo usan los reportes y los filtros de fecha de la lista de pedidos.
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

    // Inicio del dia de la tienda, en la hora del servidor (para comparar con lo guardado)
    public LocalDateTime startOfDay(LocalDate day) {
        return day.atStartOfDay(zone).withZoneSameInstant(serverZone).toLocalDateTime();
    }

    // Dia de la tienda de una fecha guardada
    public LocalDate dayOf(LocalDateTime stored) {
        return stored.atZone(serverZone).withZoneSameInstant(zone).toLocalDate();
    }
}
