package com.kleinkauf.product.dto.platform;

/*
A tenant's (non-deleted) products and bundles, for the platform dashboard's per-tenant usage.
The frontend joins it by schema with the usage users provides (GET /api/v1/platform/admin/usage).
 */
public record ProductUsageDto(
        String schema,
        long products,
        long bundles
) {
}
