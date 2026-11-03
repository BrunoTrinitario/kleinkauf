package com.decor.observability.dto;

import java.util.Map;

/*
Salud de una instancia segun su /ready: UP o DOWN y el estado de cada componente (db, redis, discoveryComposite...).
error: por que no se pudo leer (sin respuesta, timeout); null si respondio.
 */
public record InstanceHealthDto(String id, String status, Map<String, String> components, String error) {
}
