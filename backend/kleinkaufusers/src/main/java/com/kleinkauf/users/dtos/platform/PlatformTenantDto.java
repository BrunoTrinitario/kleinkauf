package com.kleinkauf.users.dtos.platform;

import java.time.OffsetDateTime;

// Vista de un tenant para el alta y el panel del superadmin
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
