package com.kleinkauf.product.dto;

import java.math.BigDecimal;

/*
A product's price, cost and margin (administrator only).
margin = price - cost; marginPercent = margin over the sale price.
They are null if the price or the cost is missing.
soldCount: units sold in paid orders (sales counter, dashboard only).
 */
public record PricingDto(
        Long productId,
        BigDecimal price,
        BigDecimal cost,
        BigDecimal margin,
        BigDecimal marginPercent,
        int soldCount
) {
}
