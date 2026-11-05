package com.kleinkauf.sales.dto.reports;

import java.time.LocalDate;
import java.util.List;

/*
Sales summary for [from, to] (store days, inclusive) in the zone zone.
periods has every period in the range, including those without sales (at zero).
 */
public record SalesSummaryDto(
        LocalDate from,
        LocalDate to,
        String groupBy,
        String zone,
        ReportTotalsDto totals,
        List<PeriodDto> periods,
        List<BreakdownDto> byChannel,
        List<BreakdownDto> byPaymentMethod
) {
}
