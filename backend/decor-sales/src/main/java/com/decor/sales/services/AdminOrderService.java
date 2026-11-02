package com.decor.sales.services;

import com.decor.common.servlet.exceptions.ApiException;
import com.decor.sales.constants.ErrorMessages;
import com.decor.sales.dto.AdminOrderDto;
import com.decor.sales.dto.OrderSummaryDto;
import com.decor.sales.dto.PageDto;
import com.decor.sales.entities.Order;
import com.decor.sales.entities.OrderStatus;
import com.decor.sales.entities.PaymentMethod;
import com.decor.sales.entities.SalesChannel;
import com.decor.sales.repositories.OrderRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Pedidos en el panel del administrador: lista con filtros y detalle
@Service
public class AdminOrderService {

    @Autowired
    private OrderRepository orderRepository;

    // Filtros de la lista; todos opcionales. from y to son fechas (yyyy-MM-dd) inclusive
    public record Filter(String status, String channel, String paymentMethod, Boolean stockReview,
                         String from, String to, String q) {
    }

    @Transactional(readOnly = true)
    public PageDto<OrderSummaryDto> list(Filter filter, Integer page, Integer size) throws ApiException {
        PageRequest pageRequest = OrderService.pageRequest(page, size)
                .withSort(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return OrderService.toPage(orderRepository.findAll(specification(filter), pageRequest));
    }

    @Transactional(readOnly = true)
    public AdminOrderDto get(String number) throws ApiException {
        return orderRepository.findByNumber(number.trim().toUpperCase(Locale.ROOT))
                .map(OrderMapper::toAdminDto)
                .orElseThrow(() -> new ApiException(ErrorMessages.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    static Specification<Order> specification(Filter filter) throws ApiException {
        OrderStatus status = parse(OrderStatus.class, filter.status(), "status");
        SalesChannel channel = parse(SalesChannel.class, filter.channel(), "channel");
        PaymentMethod paymentMethod = parse(PaymentMethod.class, filter.paymentMethod(), "paymentMethod");
        LocalDate from = parseDate(filter.from(), "from");
        LocalDate to = parseDate(filter.to(), "to");
        String q = filter.q() == null || filter.q().isBlank() ? null : filter.q().trim();

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (channel != null) predicates.add(cb.equal(root.get("channel"), channel));
            if (paymentMethod != null) predicates.add(cb.equal(root.get("paymentMethod"), paymentMethod));
            if (filter.stockReview() != null) predicates.add(cb.equal(root.get("stockReview"), filter.stockReview()));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
            if (to != null) predicates.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));
            if (q != null) {
                // Por numero (exacto, sin importar mayusculas) o por parte del email
                predicates.add(cb.or(
                        cb.equal(root.get("number"), q.toUpperCase(Locale.ROOT)),
                        cb.like(root.get("buyerEmail"), "%" + escapeLike(q.toLowerCase(Locale.ROOT)) + "%", '\\')));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String name) throws ApiException {
        if (value == null || value.isBlank()) return null;
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

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
