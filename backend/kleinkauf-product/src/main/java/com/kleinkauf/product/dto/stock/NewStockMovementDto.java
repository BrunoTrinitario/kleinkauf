package com.kleinkauf.product.dto.stock;

import com.kleinkauf.product.entities.StockMovementType;

/*
ENTRADA and SALIDA: quantity is the number of units (greater than 0).
AJUSTE: quantity is the real counted stock (0 or more) and reason is required.
 */
public record NewStockMovementDto(
        StockMovementType type,
        Integer quantity,
        String reason
) {
}
