package com.decor.sales.dto;

import com.decor.common.dto.stock.ReservationItemType;

import java.math.BigDecimal;

// price: precio cobrado si el administrador lo cambia; null = el precio actual (obligatorio si es 'a consultar')
public record LocalOrderItemDto(
        ReservationItemType type,
        Long id,
        Integer quantity,
        BigDecimal price
) {
}
