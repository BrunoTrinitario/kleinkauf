package com.kleinkauf.users.dtos.platform;

import java.time.OffsetDateTime;

/*
A tenant's usage according to users (GET /api/v1/platform/admin/usage). products provides the products and bundles per schema.
activeUsers: logged in within the last 30 days. lastActivity: the last login of a store user (null if nobody logged in).
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
