package com.kleinkauf.sales.dto.reports;

// Totals of a channel (ONLINE / LOCAL) or of a payment method
public record BreakdownDto(
        String key,
        ReportTotalsDto totals
) {
}
