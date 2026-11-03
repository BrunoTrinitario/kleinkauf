package com.kleinkauf.common.dto.stock;

import java.math.BigDecimal;

/*
Datos del item al momento de reservar. unitCost null si algun producto no tiene costo cargado.
unitPrice null solo en ventas del local de items sin precio ('a consultar').
 */
public record ReservedItemDto(
        ReservationItemType type,
        Long id,
        String name,
        BigDecimal unitPrice,
        BigDecimal unitCost
) {
}
