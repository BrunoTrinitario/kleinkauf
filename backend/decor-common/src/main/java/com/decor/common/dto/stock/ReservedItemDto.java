package com.decor.common.dto.stock;

import java.math.BigDecimal;

// Datos del item al momento de reservar. unitCost null si algun producto no tiene costo cargado
public record ReservedItemDto(
        ReservationItemType type,
        Long id,
        String name,
        BigDecimal unitPrice,
        BigDecimal unitCost
) {
}
