package com.kleinkauf.users.util;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.users.constants.ErrorMessages;
import com.kleinkauf.users.dtos.platform.PeriodCountDto;
import com.kleinkauf.users.dtos.platform.RegistrationsDto;
import org.springframework.http.HttpStatus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/*
Dashboard series per period: parameters from, to (yyyy-MM-dd, inclusive) and groupBy (DAY, WEEK or MONTH).
Without dates: the last 30 days up to today, by day. The week starts on Monday.
Every period in the range is returned, including those with 0.
Capped at MAX_PERIODS periods so a multi-year range by day does not build a huge response.
Days are in the dashboards' zone (DashboardTime). sales has its own version in ReportService.
 */
public final class Periods {

    public enum GroupBy { DAY, WEEK, MONTH }

    public static final int DEFAULT_DAYS = 30;
    public static final int MAX_PERIODS = 1000;

    public record Range(LocalDate from, LocalDate to, GroupBy groupBy) {
    }

    private Periods() {
    }

    public static Range parse(String from, String to, String groupBy, LocalDate today) throws ApiException {
        GroupBy group = parseGroupBy(groupBy);
        LocalDate end = parseDate(to, "to");
        if (end == null) end = today;
        LocalDate start = parseDate(from, "from");
        if (start == null) start = end.minusDays(DEFAULT_DAYS - 1);
        if (start.isAfter(end)) {
            throw new ApiException(ErrorMessages.INVALID_PERIOD_RANGE.formatted(MAX_PERIODS), HttpStatus.BAD_REQUEST);
        }
        Range range = new Range(start, end, group);
        if (countPeriods(range) > MAX_PERIODS) {
            throw new ApiException(ErrorMessages.INVALID_PERIOD_RANGE.formatted(MAX_PERIODS), HttpStatus.BAD_REQUEST);
        }
        return range;
    }

    // Counts the dates in the range per period; those outside are ignored
    public static RegistrationsDto series(Range range, Collection<LocalDate> dates) {
        Map<LocalDate, Long> counts = new TreeMap<>();
        for (LocalDate start = periodStart(range.from(), range.groupBy()); !start.isAfter(range.to()); start = next(start, range.groupBy())) {
            counts.put(start, 0L);
        }
        for (LocalDate date : dates) {
            if (date == null || date.isBefore(range.from()) || date.isAfter(range.to())) continue;
            counts.merge(periodStart(date, range.groupBy()), 1L, Long::sum);
        }
        List<PeriodCountDto> periods = new ArrayList<>();
        counts.forEach((period, count) -> periods.add(new PeriodCountDto(period, count)));
        return new RegistrationsDto(range.from(), range.to(), range.groupBy().name(), periods);
    }

    public static LocalDate periodStart(LocalDate day, GroupBy group) {
        return switch (group) {
            case DAY -> day;
            case WEEK -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> day.withDayOfMonth(1);
        };
    }

    private static LocalDate next(LocalDate start, GroupBy group) {
        return switch (group) {
            case DAY -> start.plusDays(1);
            case WEEK -> start.plusWeeks(1);
            case MONTH -> start.plusMonths(1);
        };
    }

    private static long countPeriods(Range range) {
        LocalDate first = periodStart(range.from(), range.groupBy());
        LocalDate last = periodStart(range.to(), range.groupBy());
        ChronoUnit unit = switch (range.groupBy()) {
            case DAY -> ChronoUnit.DAYS;
            case WEEK -> ChronoUnit.WEEKS;
            case MONTH -> ChronoUnit.MONTHS;
        };
        return unit.between(first, last) + 1;
    }

    private static GroupBy parseGroupBy(String value) throws ApiException {
        if (value == null || value.isBlank()) return GroupBy.DAY;
        try {
            return GroupBy.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorMessages.INVALID_FILTER.formatted("groupBy"), HttpStatus.BAD_REQUEST);
        }
    }

    private static LocalDate parseDate(String value, String name) throws ApiException {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new ApiException(ErrorMessages.INVALID_FILTER.formatted(name), HttpStatus.BAD_REQUEST);
        }
    }
}
