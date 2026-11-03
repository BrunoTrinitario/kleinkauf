package com.kleinkauf.common.dto.stock;

public record ReservationItemDto(
        ReservationItemType type,
        Long id,
        Integer quantity
) {
}
