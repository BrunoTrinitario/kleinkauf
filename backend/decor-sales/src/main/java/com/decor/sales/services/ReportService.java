package com.decor.sales.services;

import com.decor.common.dto.stock.ReservationItemType;
import com.decor.common.servlet.exceptions.ApiException;
import com.decor.sales.constants.ErrorMessages;
import com.decor.sales.dto.reports.BreakdownDto;
import com.decor.sales.dto.reports.PeriodDto;
import com.decor.sales.dto.reports.ProductReportDto;
import com.decor.sales.dto.reports.ProductRankingDto;
import com.decor.sales.dto.reports.ReportTotalsDto;
import com.decor.sales.dto.reports.SalesSummaryDto;
import com.decor.sales.entities.Order;
import com.decor.sales.entities.OrderItem;
import com.decor.sales.entities.OrderStatus;
import com.decor.sales.entities.PaymentMethod;
import com.decor.sales.entities.SalesChannel;
import com.decor.sales.repositories.OrderRepository;
import com.decor.sales.util.SalesTime;
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
Reportes de ventas del panel. Una venta es un pedido en PAGADO, EN_PREPARACION, LISTO o ENTREGADO
(los cancelados despues de pagar dejan de contar), con fecha de pago en el rango.
Los dias son de la tienda (SalesTime). Los montos usan los precios y costos guardados al vender.
Se calculan al pedirlos, sin cache: leen solo las ventas del rango (hasta 366 dias) de una tienda.
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

    // Rango de dias de la tienda, inclusive. Sin fechas: los ultimos 30 dias hasta hoy
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
        // Mas viejas primero: el nombre que queda es el de la venta mas reciente
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

    // Pedidos pagos antes de la etapa 3 no tienen paid_at (ver OrderRepository.findSales)
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

    // Acumulador de un conjunto de pedidos
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

    // Acumulador de un producto o combo
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
