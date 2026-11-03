package com.kleinkauf.sales.dto;

import com.kleinkauf.common.dto.stock.ReservationItemType;

import java.math.BigDecimal;

// Linea del pedido para el administrador: con precio de lista y costo
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
