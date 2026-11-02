package com.decor.users.dtos.platform;

/*
Alta de un tenant con su dueno.
identifier: 'mitienda' -> URL https://mitienda.<dominio> y schema 'mitienda' ('-' pasa a '_').
 */
public record NewTenantDto(
        String identifier,
        String ownerName,
        String ownerEmail,
        String ownerPassword
) {
}
