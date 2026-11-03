package com.decor.observability.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/*
Uso de una tienda (canal store.usage y GET /api/v1/observability/store/usage):
- date, catalogVisits y topProducts: el catalogo publico en el dia (lo cuenta products, ver UsageKeys).
- traffic: requests y errores por segundo de la tienda en el gateway, de la ultima lectura del recolector.
  null hasta la segunda lectura (las tasas salen de restar dos) o si el gateway no respondio.
 */
public record StoreUsageDto(
        Instant at,
        LocalDate date,
        long catalogVisits,
        List<ProductViews> topProducts,
        Traffic traffic
) {

    public record ProductViews(String slug, long views) {
    }

    public record Traffic(double requestsPerSecond, double errors4xxPerSecond, double errors5xxPerSecond) {
    }
}
