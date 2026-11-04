package com.kleinkauf.observability.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/*
A store's usage (store.usage channel and GET /api/v1/observability/store/usage):
- date, catalogVisits and topProducts: the public catalog on that day (counted by products, see UsageKeys).
- traffic: the store's requests and errors per second at the gateway, from the collector's last reading.
  null until the second reading (rates come from subtracting two) or if the gateway did not answer.
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
