package com.decor.users.dtos.platform;

import java.time.LocalDate;
import java.util.List;

// Serie de altas: todos los periodos del rango, tambien los que tienen 0
public record RegistrationsDto(
        LocalDate from,
        LocalDate to,
        String groupBy,
        List<PeriodCountDto> periods
) {
}
