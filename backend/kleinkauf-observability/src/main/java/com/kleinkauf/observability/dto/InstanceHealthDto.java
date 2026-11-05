package com.kleinkauf.observability.dto;

import java.util.Map;

/*
An instance's health according to its /ready: UP or DOWN and the status of each component (db, redis, discoveryComposite...).
error: why it could not be read (no response, timeout); null if it answered.
 */
public record InstanceHealthDto(String id, String status, Map<String, String> components, String error) {
}
