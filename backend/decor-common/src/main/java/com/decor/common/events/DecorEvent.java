package com.decor.common.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/*
Evento que un servicio publica en Redis Streams (stream decor:events) para observability, que lo reenvia en
vivo por WebSocket (docs/observabilidad.md). En el stream cada evento es un registro con los campos
type, tenant, at (UTC, ISO-8601) y data (JSON).
tenant: el schema de la tienda, o "public" si es de la plataforma.
 */
public record DecorEvent(String type, String tenant, Instant at, Map<String, Object> data) {

    public static final String STREAM = "decor:events";

    // Tiendas (plataforma): altas, habilitar / deshabilitar y bajas
    public static final String TENANT_CREATED = "tenant.created";
    public static final String TENANT_ENABLED = "tenant.enabled";
    public static final String TENANT_DISABLED = "tenant.disabled";
    public static final String TENANT_DELETED = "tenant.deleted";

    // Actividad de una tienda (canal store.activity): pedidos y ventas del local (sales) y stock (products)
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

    // Los datos pueden tener valores null (ej. un movimiento de stock sin motivo): Map.copyOf no los acepta y el
    // NullPointerException tiraba abajo la operacion que publicaba el evento
    public DecorEvent {
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

    // null si el registro no tiene la forma de un evento (lo escribio otro, o una version vieja)
    public static DecorEvent fromFields(Map<String, String> fields, ObjectMapper mapper) {
        String type = fields.get(FIELD_TYPE);
        String tenant = fields.get(FIELD_TENANT);
        String at = fields.get(FIELD_AT);
        if (type == null || tenant == null || at == null) {
            return null;
        }
        try {
            String data = fields.get(FIELD_DATA);
            return new DecorEvent(type, tenant, Instant.parse(at),
                    data == null ? Map.of() : mapper.readValue(data, DATA_TYPE));
        } catch (Exception e) {
            return null;
        }
    }
}
