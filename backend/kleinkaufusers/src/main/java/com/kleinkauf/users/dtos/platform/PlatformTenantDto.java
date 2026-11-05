package com.kleinkauf.users.dtos.platform;

import java.time.OffsetDateTime;

// View of a tenant for the sign-up and the superadmin dashboard
public record PlatformTenantDto(
        Long id,
        String identifier,
        String schema,
        String url,
        String configId,
        boolean enabled,
        OffsetDateTime createdAt
) {
}
