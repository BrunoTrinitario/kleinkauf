package com.kleinkauf.sales.dto.reports;

import com.kleinkauf.common.dto.stock.ReservationItemType;

import java.math.BigDecimal;

/*
A product or bundle in the ranking. name is the one from the most recent sale.
revenue: subtotals of its lines (before the order discount, which is not spread across the items).
profit: revenue - cost; if there are units without cost (unitsWithoutCost > 0), it is higher than the real one.
 */
public record ProductRankingDto(
        ReservationItemType type,
        Long id,
        String name,
        long units,
        BigDecimal revenue,
        BigDecimal cost,
        BigDecimal profit,
        long unitsWithoutCost
) {
}
