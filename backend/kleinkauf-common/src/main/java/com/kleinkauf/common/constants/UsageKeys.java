package com.kleinkauf.common.constants;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

/*
Catalog usage of each store in Redis (docs/observabilidad.md, store.usage channel): one hash per store and day,
usage:<tenant>:<yyyy-MM-dd>, with
- visits: times the catalog listing was opened (the first page),
- product:<slug>: times that product's detail was opened.
Written by products and read by observability. Expires after TTL: it is for the dashboard, not a history.
The day is the one in ZONE (there is no per-store zone yet).
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
