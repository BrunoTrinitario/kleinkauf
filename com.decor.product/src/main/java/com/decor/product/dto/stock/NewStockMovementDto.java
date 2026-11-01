package com.decor.product.dto.stock;

import com.decor.product.entities.StockMovementType;

/*
ENTRADA y SALIDA: quantity es la cantidad de unidades (mayor a 0).
AJUSTE: quantity es el stock real contado (0 o mas) y reason es obligatorio.
 */
public record NewStockMovementDto(
        StockMovementType type,
        Integer quantity,
        String reason
) {
}
