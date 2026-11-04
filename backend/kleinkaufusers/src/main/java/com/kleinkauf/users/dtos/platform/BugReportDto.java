package com.kleinkauf.users.dtos.platform;

import java.time.OffsetDateTime;

// Bug report in the platform dashboard. store and storeUrl: null if it was sent without a store
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
