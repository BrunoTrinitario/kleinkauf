package com.kleinkauf.sales.dto.reports;

// Totales de un canal (ONLINE / LOCAL) o de un medio de pago
public record BreakdownDto(
        String key,
        ReportTotalsDto totals
) {
}
