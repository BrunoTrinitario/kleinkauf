package com.kleinkauf.sales.services;

import com.kleinkauf.common.dto.stock.ReservationItemType;
import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.sales.constants.ErrorMessages;
import com.kleinkauf.sales.dto.reports.BreakdownDto;
import com.kleinkauf.sales.dto.reports.PeriodDto;
import com.kleinkauf.sales.dto.reports.ProductReportDto;
import com.kleinkauf.sales.dto.reports.ProductRankingDto;
import com.kleinkauf.sales.dto.reports.ReportTotalsDto;
import com.kleinkauf.sales.dto.reports.SalesSummaryDto;
import com.kleinkauf.sales.entities.Order;
import com.kleinkauf.sales.entities.OrderItem;
import com.kleinkauf.sales.entities.OrderStatus;
import com.kleinkauf.sales.entities.PaymentMethod;
import com.kleinkauf.sales.entities.SalesChannel;
import com.kleinkauf.sales.repositories.OrderRepository;
import com.kleinkauf.sales.util.SalesTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/*
Dashboard sales reports. A sale is an order in PAGADO, EN_PREPARACION, LISTO or ENTREGADO
(those cancelled after paying stop counting), with a payment date in the range.
Days are store days (SalesTime). Amounts use the prices and costs stored at sale time.
Computed on request, without cache: they only read the store's sales in the range (up to 366 days).
 */
@Service
public class ReportService {

    public enum GroupBy { DAY, WEEK, MONTH }

    public enum RankingSort { UNITS, REVENUE, PROFIT }

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 366;
    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;
    private static final Set<OrderStatus> SALES = EnumSet.of(
            OrderStatus.PAGADO, OrderStatus.EN_PREPARACION, OrderStatus.LISTO, OrderStatus.ENTREGADO);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private SalesTime salesTime;

    // Range of store days, inclusive. Without dates: the last 30 days up to today
    private record Range(LocalDate from, LocalDate to) {
    }

    @Transactional(readOnly = true)
    public SalesSummaryDto summary(String from, String to, String groupBy, String channel, String paymentMethod) throws ApiException {
        Range range = range(from, to);
        GroupBy group = parse(GroupBy.class, groupBy, GroupBy.DAY, "groupBy");
        List<Order> sales = sales(range, parse(SalesChannel.class, channel, null, "channel"),
                parse(PaymentMethod.class, paymentMethod, null, "paymentMethod"));

        Totals totals = new Totals();
        Map<LocalDate, Totals> periods = new TreeMap<>();
        for (LocalDate start = periodStart(range.from(), group); !start.isAfter(range.to()); start = next(start, group)) {
            periods.put(start, new Totals());
        }
        Map<SalesChannel, Totals> byChannel = new LinkedHashMap<>();
        Map<PaymentMethod, Totals> byPaymentMethod = new LinkedHashMap<>();

        for (Order order : sales) {
            LocalDate day = salesTime.dayOf(saleDate(order));
            totals.add(order);
            periods.computeIfAbsent(periodStart(day, group), d -> new Totals()).add(order);
            byChannel.computeIfAbsent(order.getChannel(), c -> new Totals()).add(order);
            byPaymentMethod.computeIfAbsent(order.getPaymentMethod(), p -> new Totals()).add(order);
        }

        return new SalesSummaryDto(range.from(), range.to(), group.name(), salesTime.zone().getId(), totals.dto(),
                periods.entrySet().stream().map(e -> new PeriodDto(e.getKey(), e.getValue().dto())).toList(),
                breakdown(byChannel), breakdown(byPaymentMethod));
    }

    @Transactional(readOnly = true)
    public ProductReportDto products(String from, String to, String channel, String sort, Integer limit) throws ApiException {
        Range range = range(from, to);
        RankingSort rankingSort = parse(RankingSort.class, sort, RankingSort.UNITS, "sort");
        int max = limit == null ? DEFAULT_LIMIT : limit;
        if (max < 1 || max > MAX_LIMIT) {
            throw new ApiException(ErrorMessages.INVALID_FILTER.formatted("limit"), HttpStatus.BAD_REQUEST);
        }
        List<Order> sales = sales(range, parse(SalesChannel.class, channel, null, "channel"), null);
        // Oldest first: the name that remains is the one from the most recent sale
        sales.sort(Comparator.comparing(ReportService::saleDate));

        Map<String, Ranking> ranking = new LinkedHashMap<>();
        for (Order order : sales) {
            for (OrderItem item : order.getItems()) {
                ranking.computeIfAbsent(item.getType() + ":" + item.getItemId(), k -> new Ranking(item.getType(), item.getItemId()))
                        .add(item);
            }
        }

        Function<Ranking, BigDecimal> key = switch (rankingSort) {
            case UNITS -> r -> BigDecimal.valueOf(r.units);
            case REVENUE -> r -> r.revenue;
            case PROFIT -> r -> r.revenue.subtract(r.cost);
        };
        List<ProductRankingDto> items = ranking.values().stream()
                .sorted(Comparator.comparing(key).reversed().thenComparing(r -> r.name, String.CASE_INSENSITIVE_ORDER))
                .limit(max)
                .map(Ranking::dto)
                .toList();
        return new ProductReportDto(range.from(), range.to(), rankingSort.name(), items);
    }

    private List<Order> sales(Range range, SalesChannel channel, PaymentMethod paymentMethod) {
        return new ArrayList<>(orderRepository.findSales(SALES, salesTime.startOfDay(range.from()),
                salesTime.startOfDay(range.to().plusDays(1)), channel, paymentMethod));
    }

    // Orders paid before stage 3 have no paid_at (see OrderRepository.findSales)
    private static LocalDateTime saleDate(Order order) {
        return order.getPaidAt() != null ? order.getPaidAt() : order.getCreatedAt();
    }

    private Range range(String from, String to) throws ApiException {
        LocalDate end = parseDate(to, "to");
        if (end == null) end = salesTime.today();
        LocalDate start = parseDate(from, "from");
        if (start == null) start = end.minusDays(DEFAULT_DAYS - 1);
        if (start.isAfter(end) || start.plusDays(MAX_DAYS).isBefore(end.plusDays(1))) {
            throw new ApiException(ErrorMessages.INVALID_REPORT_RANGE.formatted(MAX_DAYS), HttpStatus.BAD_REQUEST);
        }
        return new Range(start, end);
    }

    static LocalDate periodStart(LocalDate day, GroupBy group) {
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

    private static <K extends Enum<K>> List<BreakdownDto> breakdown(Map<K, Totals> totals) {
        return totals.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new BreakdownDto(e.getKey().name(), e.getValue().dto()))
                .toList();
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, E defaultValue, String name) throws ApiException {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorMessages.INVALID_FILTER.formatted(name), HttpStatus.BAD_REQUEST);
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

    // Accumulator for a set of orders
    private static class Totals {
        long orders;
        long units;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal discounts = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        long unitsWithoutCost;

        void add(Order order) {
            orders++;
            revenue = revenue.add(order.getTotal());
            discounts = discounts.add(order.getDiscount());
            for (OrderItem item : order.getItems()) {
                units += item.getQuantity();
                if (item.getUnitCost() == null) {
                    unitsWithoutCost += item.getQuantity();
                } else {
                    cost = cost.add(item.getUnitCost().multiply(BigDecimal.valueOf(item.getQuantity())));
                }
            }
        }

        ReportTotalsDto dto() {
            return new ReportTotalsDto(orders, units, revenue, discounts, cost, revenue.subtract(cost), unitsWithoutCost);
        }
    }

    // Accumulator for a product or bundle
    private static class Ranking {
        final ReservationItemType type;
        final Long id;
        String name;
        long units;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        long unitsWithoutCost;

        Ranking(ReservationItemType type, Long id) {
            this.type = type;
            this.id = id;
        }

        void add(OrderItem item) {
            name = item.getName();
            units += item.getQuantity();
            revenue = revenue.add(item.getSubtotal());
            if (item.getUnitCost() == null) {
                unitsWithoutCost += item.getQuantity();
            } else {
                cost = cost.add(item.getUnitCost().multiply(BigDecimal.valueOf(item.getQuantity())));
            }
        }

        ProductRankingDto dto() {
            return new ProductRankingDto(type, id, name, units, revenue, cost, revenue.subtract(cost), unitsWithoutCost);
        }
    }
}
