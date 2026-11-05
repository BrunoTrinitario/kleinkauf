package com.kleinkauf.users.dtos.platform;

import java.util.List;

// A page of reports (same format as the other listings). pending: how many remain unresolved in total
public record BugReportPageDto(
        List<BugReportDto> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        long pending
) {
}
