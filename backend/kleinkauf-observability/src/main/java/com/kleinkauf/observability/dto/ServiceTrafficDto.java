package com.kleinkauf.observability.dto;

/*
Trafico y recursos de un servicio, sumando sus instancias. Las tasas son por segundo entre las dos ultimas
lecturas; null si todavia no hay dos lecturas. Los percentiles (ms) son null si no hubo requests.
cpu: promedio de las instancias (0 a 1).
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
