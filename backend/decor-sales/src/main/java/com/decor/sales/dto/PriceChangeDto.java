package com.decor.sales.dto;

import com.decor.common.dto.stock.ReservationItemType;

import java.math.BigDecimal;

// Item cuyo precio cambio entre el carrito y el pedido
public record PriceChangeDto(
        ReservationItemType type,
        Long id,
        String name,
        BigDecimal expectedPrice,
        BigDecimal currentPrice
) {
}
