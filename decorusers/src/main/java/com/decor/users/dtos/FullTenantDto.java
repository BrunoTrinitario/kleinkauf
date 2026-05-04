package com.decor.users.dtos;

import java.time.LocalDateTime;

public record FullTenantDto(
        String id,
        String name,
        String url,
        String dbName,
        String dbUser,
        String dbPassword,
        LocalDateTime createdAt,
        LocalDateTime deletedAt,
        boolean enabled
) {
}
