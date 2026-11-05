package com.kleinkauf.sales.dto;

import com.kleinkauf.common.dto.stock.ReservationItemType;

import java.math.BigDecimal;

// Order line for the administrator: with list price and cost
public record AdminOrderItemDto(
        ReservationItemType type,
        Long id,
        String name,
        int quantity,
        BigDecimal listPrice,
        BigDecimal unitPrice,
        BigDecimal unitCost,
        BigDecimal subtotal
) {
}
