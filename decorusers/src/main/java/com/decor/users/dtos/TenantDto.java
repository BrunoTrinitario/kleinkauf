package com.decor.users.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

public record TenantDto(
        Long id,
        String configId,
        String schema,
        Boolean enable,
        LocalDateTime createdAt,
        LocalDateTime deletedAt
) {
}
