package com.decor.sales.dto;

import com.decor.common.dto.stock.ReservationItemType;

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
