package com.decor.product.entities;

public enum StockMovementType {
    // Suma unidades (compra, devolucion)
    ENTRADA,
    // Resta unidades (venta, rotura, perdida)
    SALIDA,
    // Fija el stock a un valor (conteo fisico)
    AJUSTE
}
