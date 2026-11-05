package com.kleinkauf.sales.dto;

import com.kleinkauf.common.dto.stock.ReservationItemType;

import java.math.BigDecimal;

// price: price charged if the administrator changes it; null = the current price (required if it is 'a consultar')
public record LocalOrderItemDto(
        ReservationItemType type,
        Long id,
        Integer quantity,
        BigDecimal price
) {
}
