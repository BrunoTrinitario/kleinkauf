package com.kleinkauf.common.dto.stock;

import java.util.List;

// Items de un pedido para sumar o restar el contador de vendidos de cada producto y combo
public record SoldItemsDto(
        List<ReservationItemDto> items
) {
}
