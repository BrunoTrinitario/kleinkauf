package com.kleinkauf.product.dto;

import java.math.BigDecimal;

/*
Precio, costo y margen de un producto (solo administrador).
margin = price - cost; marginPercent = margen sobre el precio de venta.
Son null si falta el precio o el costo.
soldCount: unidades vendidas en pedidos pagos (contador de sales, solo para el panel).
 */
public record PricingDto(
        Long productId,
        BigDecimal price,
        BigDecimal cost,
        BigDecimal margin,
        BigDecimal marginPercent,
        int soldCount
) {
}
