package com.decor.users.dtos.platform;

// GET /api/v1/platform/admin/dashboard
public record PlatformDashboardDto(
        TenantCountsDto tenants,
        RegistrationsDto registrations
) {
}
