package com.kleinkauf.observability.dto;

import java.util.List;

/*
A service's health: UP (all its instances ready), DEGRADED (some) or DOWN (none, or there are no
instances in eureka). openCircuitBreakers: the circuit breakers open on any of its instances.
 */
public record ServiceHealthDto(String name, String status, List<InstanceHealthDto> instances,
                               List<String> openCircuitBreakers) {

    public static final String UP = "UP";
    public static final String DEGRADED = "DEGRADED";
    public static final String DOWN = "DOWN";
}
