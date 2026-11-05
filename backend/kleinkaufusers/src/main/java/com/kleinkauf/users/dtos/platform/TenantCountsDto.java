package com.kleinkauf.users.dtos.platform;

// total, enabled and disabled count the non-deleted tenants; deleted, the deleted ones (soft delete)
public record TenantCountsDto(
        long total,
        long enabled,
        long disabled,
        long deleted
) {
}
