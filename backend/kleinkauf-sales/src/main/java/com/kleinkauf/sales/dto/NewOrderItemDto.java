package com.kleinkauf.sales.dto;

import com.kleinkauf.common.dto.stock.ReservationItemType;

import java.math.BigDecimal;

// price: the price the buyer saw in the cart. If it changed, the order is not created (409)
public record NewOrderItemDto(
        ReservationItemType type,
        Long id,
        Integer quantity,
        BigDecimal price
) {
}
