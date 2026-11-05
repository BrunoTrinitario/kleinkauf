package com.kleinkauf.sales.dto.reports;

import java.math.BigDecimal;

/*
Totals of a set of sales.
- revenue: what was charged (order totals, with the discount already subtracted).
- discounts: discounts of the in-store sales.
- cost: cost of the units sold, with the cost stored at sale time.
- profit: revenue - cost. If there are units without a cost set (unitsWithoutCost > 0), the profit is higher
  than the real one: the dashboard should warn about it.
 */
public record ReportTotalsDto(
        long orders,
        long units,
        BigDecimal revenue,
        BigDecimal discounts,
        BigDecimal cost,
        BigDecimal profit,
        long unitsWithoutCost
) {
}
