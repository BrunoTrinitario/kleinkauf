package com.kleinkauf.sales.dto;

import com.kleinkauf.common.dto.stock.ReservationItemType;

import java.math.BigDecimal;

// price: el precio que vio el comprador en el carrito. Si cambio, el pedido no se crea (409)
public record NewOrderItemDto(
        ReservationItemType type,
        Long id,
        Integer quantity,
        BigDecimal price
) {
}
