package com.decor.gateway.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

public record TenantDto(
        UUID id,
        String configId,
        String name,
        String url,
        String dbName,
        String dbUser,
        String dbPassword,
        Boolean enable,
        LocalDateTime createdAt,
        LocalDateTime deletedAt,
        String driverClassName
) {
}
