package com.kleinkauf.sales.dto.reports;

import java.time.LocalDate;

// Un periodo del resumen: start es el dia, el lunes de la semana o el dia 1 del mes
public record PeriodDto(
        LocalDate start,
        ReportTotalsDto totals
) {
}
