package com.decor.sales.dto;

import com.decor.common.dto.stock.ReservationItemType;

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
