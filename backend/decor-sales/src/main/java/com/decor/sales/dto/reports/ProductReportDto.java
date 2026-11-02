package com.decor.sales.dto.reports;

import java.time.LocalDate;
import java.util.List;

public record ProductReportDto(
        LocalDate from,
        LocalDate to,
        String sort,
        List<ProductRankingDto> items
) {
}
