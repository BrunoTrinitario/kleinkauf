package com.decor.users.dtos.platform;

import java.time.OffsetDateTime;

/*
Uso de un tenant segun users (GET /api/v1/platform/admin/usage). products da los productos y combos por schema.
activeUsers: entraron en los ultimos 30 dias. lastActivity: el ultimo login de un usuario de la tienda (null si nadie entro).
 */
public record TenantUsageDto(
        Long id,
        String identifier,
        String schema,
        boolean enabled,
        OffsetDateTime createdAt,
        long users,
        long activeUsers,
        OffsetDateTime lastActivity
) {
}
