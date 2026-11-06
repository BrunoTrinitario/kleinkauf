package com.kleinkauf.common.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/*
Event a service publishes to Redis Streams (stream kleinkauf:events) for observability, which forwards it
live over WebSocket (docs/features/observability.md). In the stream each event is a record with the fields
type, tenant, at (UTC, ISO-8601) and data (JSON).
tenant: the store's schema, or "public" if it belongs to the platform.
 */
public record KleinKaufEvent(String type, String tenant, Instant at, Map<String, Object> data) {

    public static final String STREAM = "kleinkauf:events";

    // Stores (platform): creation, enable / disable and deletion
    public static final String TENANT_CREATED = "tenant.created";
    public static final String TENANT_ENABLED = "tenant.enabled";
    public static final String TENANT_DISABLED = "tenant.disabled";
    public static final String TENANT_DELETED = "tenant.deleted";

    // A store's activity (store.activity channel): orders and in-store sales (sales) and stock (products)
    public static final String ORDER_CREATED = "order.created";
    public static final String ORDER_RECEIPT_UPLOADED = "order.receipt-uploaded";
    public static final String STOCK_MOVEMENT = "stock.movement";
    public static final String STOCK_LOW = "stock.low";

    static final String FIELD_TYPE = "type";
    static final String FIELD_TENANT = "tenant";
    static final String FIELD_AT = "at";
    static final String FIELD_DATA = "data";

    private static final TypeReference<Map<String, Object>> DATA_TYPE = new TypeReference<>() {
    };

    // Data may contain null values (e.g. a stock movement without a reason): Map.copyOf does not accept them and the
    // NullPointerException brought down the operation that published the event
    public KleinKaufEvent {
        data = data == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    public Map<String, String> toFields(ObjectMapper mapper) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(FIELD_TYPE, type);
        fields.put(FIELD_TENANT, tenant);
        fields.put(FIELD_AT, at.toString());
        try {
            fields.put(FIELD_DATA, mapper.writeValueAsString(data));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Datos del evento " + type + " no serializables", e);
        }
        return fields;
    }

    // null if the record does not have the shape of an event (written by someone else, or an old version)
    public static KleinKaufEvent fromFields(Map<String, String> fields, ObjectMapper mapper) {
        String type = fields.get(FIELD_TYPE);
        String tenant = fields.get(FIELD_TENANT);
        String at = fields.get(FIELD_AT);
        if (type == null || tenant == null || at == null) {
            return null;
        }
        try {
            String data = fields.get(FIELD_DATA);
            return new KleinKaufEvent(type, tenant, Instant.parse(at),
                    data == null ? Map.of() : mapper.readValue(data, DATA_TYPE));
        } catch (Exception e) {
            return null;
        }
    }
}
