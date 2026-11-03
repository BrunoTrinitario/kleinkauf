package com.kleinkauf.users.dtos.platform;

import java.time.LocalDate;

// Cantidad de un periodo: period es su primer dia (el lunes en WEEK, el 1 en MONTH)
public record PeriodCountDto(
        LocalDate period,
        long count
) {
}
