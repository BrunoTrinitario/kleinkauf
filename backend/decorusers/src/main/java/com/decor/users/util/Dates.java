package com.decor.users.util;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/*
Las fechas se guardan como LocalDateTime en la hora del servidor (en Docker, UTC). Sin el offset, el navegador
las lee como hora local y las muestra corridas: las respuestas de la plataforma las mandan con su offset.
 */
public final class Dates {

    private Dates() {
    }

    public static OffsetDateTime withServerOffset(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
