package com.kleinkauf.sales.dto;

import com.kleinkauf.common.dto.stock.ReservationItemType;

import java.math.BigDecimal;

public record OrderItemDto(
        ReservationItemType type,
        Long id,
        String name,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {
}
