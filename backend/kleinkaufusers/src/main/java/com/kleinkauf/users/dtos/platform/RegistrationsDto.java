package com.kleinkauf.users.dtos.platform;

import java.time.LocalDate;
import java.util.List;

// Sign-up series: every period in the range, including those with 0
public record RegistrationsDto(
        LocalDate from,
        LocalDate to,
        String groupBy,
        List<PeriodCountDto> periods
) {
}
