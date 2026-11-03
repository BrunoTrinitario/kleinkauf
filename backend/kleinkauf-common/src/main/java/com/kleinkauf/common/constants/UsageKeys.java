package com.kleinkauf.common.constants;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

/*
Uso del catalogo de cada tienda en Redis (docs/observabilidad.md, canal store.usage): un hash por tienda y dia,
usage:<tenant>:<yyyy-MM-dd>, con
- visits: veces que se abrio el listado del catalogo (la primera pagina),
- product:<slug>: veces que se abrio el detalle de ese producto.
Lo escribe products y lo lee observability. Vence a los TTL: es para el panel, no un historial.
El dia es el de ZONE (todavia no hay zona por tienda).
 */
public final class UsageKeys {

    public static final String PREFIX = "usage:";
    public static final String VISITS = "visits";
    public static final String PRODUCT_PREFIX = "product:";
    public static final Duration TTL = Duration.ofDays(8);
    public static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private UsageKeys() {
    }

    public static String key(String tenant, LocalDate day) {
        return PREFIX + tenant + ":" + day;
    }

    public static String productField(String slug) {
        return PRODUCT_PREFIX + slug;
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }
}
