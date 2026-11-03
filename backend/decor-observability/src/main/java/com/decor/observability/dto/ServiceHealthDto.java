package com.decor.observability.dto;

import java.util.List;

/*
Salud de un servicio: UP (todas sus instancias listas), DEGRADED (algunas) o DOWN (ninguna, o no hay
instancias en eureka). openCircuitBreakers: los circuit breakers abiertos en alguna de sus instancias.
 */
public record ServiceHealthDto(String name, String status, List<InstanceHealthDto> instances,
                               List<String> openCircuitBreakers) {

    public static final String UP = "UP";
    public static final String DEGRADED = "DEGRADED";
    public static final String DOWN = "DOWN";
}
