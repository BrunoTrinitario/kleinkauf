package com.decor.users.dtos.platform;

import java.time.LocalDateTime;

// Vista de un tenant para el alta y el panel del superadmin
public record PlatformTenantDto(
        Long id,
        String identifier,
        String schema,
        String url,
        String configId,
        boolean enabled,
        LocalDateTime createdAt
) {
}
