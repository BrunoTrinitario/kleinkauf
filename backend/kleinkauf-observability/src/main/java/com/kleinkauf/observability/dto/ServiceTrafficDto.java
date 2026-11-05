package com.kleinkauf.observability.dto;

/*
Traffic and resources of a service, adding up its instances. Rates are per second between the last two
readings; null if there are not two readings yet. Percentiles (ms) are null if there were no requests.
cpu: average of the instances (0 to 1).
 */
public record ServiceTrafficDto(
        String name,
        int instances,
        Double requestsPerSecond,
        Double errors5xxPerSecond,
        Double errors4xxPerSecond,
        Double rateLimitedPerSecond,
        Double p50Ms,
        Double p95Ms,
        Double p99Ms,
        Double heapUsedBytes,
        Double heapMaxBytes,
        Double cpu,
        Double threads,
        Double dbConnectionsActive,
        Double dbConnectionsMax
) {
}
