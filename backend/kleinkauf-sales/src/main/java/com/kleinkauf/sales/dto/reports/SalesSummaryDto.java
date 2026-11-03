package com.kleinkauf.sales.dto.reports;

import java.time.LocalDate;
import java.util.List;

/*
Resumen de ventas de [from, to] (dias de la tienda, inclusive) en la zona zone.
periods tiene todos los periodos del rango, tambien los que no tuvieron ventas (en cero).
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
