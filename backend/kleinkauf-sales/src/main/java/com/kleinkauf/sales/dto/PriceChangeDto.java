package com.kleinkauf.sales.dto;

import com.kleinkauf.common.dto.stock.ReservationItemType;

import java.math.BigDecimal;

// Item whose price changed between the cart and the order
public record PriceChangeDto(
        ReservationItemType type,
        Long id,
        String name,
        BigDecimal expectedPrice,
        BigDecimal currentPrice
) {
}
