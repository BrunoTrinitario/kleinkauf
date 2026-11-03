package com.kleinkauf.users.dtos.platform;

// total, enabled y disabled cuentan los tenants no borrados; deleted, los borrados (soft delete)
public record TenantCountsDto(
        long total,
        long enabled,
        long disabled,
        long deleted
) {
}
