package com.decor.product.dto.platform;

/*
Productos (no borrados) y combos de un tenant, para el uso por tenant del panel de la plataforma.
El frontend lo junta con el uso que da users (GET /api/v1/platform/admin/usage) por schema.
 */
public record ProductUsageDto(
        String schema,
        long products,
        long bundles
) {
}
