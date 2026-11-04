package com.kleinkauf.users.dtos.platform;

import java.time.OffsetDateTime;

// Reporte de bug en el panel de la plataforma. store y storeUrl: null si se mando sin tienda
public record BugReportDto(
        Long id,
        String store,
        String storeUrl,
        Long userId,
        String reporterName,
        String email,
        String description,
        String pageUrl,
        String userAgent,
        OffsetDateTime createdAt,
        OffsetDateTime resolvedAt
) {
}
