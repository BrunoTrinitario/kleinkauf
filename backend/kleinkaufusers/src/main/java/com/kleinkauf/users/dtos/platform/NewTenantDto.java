package com.kleinkauf.users.dtos.platform;

/*
Sign-up of a tenant with its owner.
identifier: 'mitienda' -> URL https://mitienda.<domain> and schema 'mitienda' ('-' becomes '_').
 */
public record NewTenantDto(
        String identifier,
        String ownerName,
        String ownerEmail,
        String ownerPassword
) {
}
