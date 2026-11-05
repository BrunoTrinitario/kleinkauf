package com.kleinkauf.users.dtos.platform;

import java.time.LocalDate;

// Count for one period: period is its first day (the Monday in WEEK, the 1st in MONTH)
public record PeriodCountDto(
        LocalDate period,
        long count
) {
}
