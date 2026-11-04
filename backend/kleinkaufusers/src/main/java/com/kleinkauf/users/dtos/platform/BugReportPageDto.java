package com.kleinkauf.users.dtos.platform;

import java.util.List;

// Pagina de reportes (mismo formato que el resto de los listados). pending: cuantos quedan sin resolver en total
public record BugReportPageDto(
        List<BugReportDto> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        long pending
) {
}
