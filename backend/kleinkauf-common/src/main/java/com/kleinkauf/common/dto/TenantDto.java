package com.kleinkauf.common.dto;

import java.time.LocalDateTime;

public record TenantDto(
        Long id,
        String configId,
        String schema,
        Boolean enable,
        LocalDateTime createdAt,
        LocalDateTime deletedAt
) {
}
