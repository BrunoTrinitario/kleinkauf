package com.kleinkauf.sales.dto.reports;

import java.time.LocalDate;

// A summary period: start is the day, the Monday of the week or the 1st of the month
public record PeriodDto(
        LocalDate start,
        ReportTotalsDto totals
) {
}
