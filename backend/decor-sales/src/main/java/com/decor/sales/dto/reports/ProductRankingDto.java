package com.decor.sales.dto.reports;

import com.decor.common.dto.stock.ReservationItemType;

import java.math.BigDecimal;

/*
Un producto o combo en el ranking. name es el de la venta mas reciente.
revenue: subtotales de sus lineas (antes del descuento del pedido, que no se reparte entre los items).
profit: revenue - cost; si hay unidades sin costo (unitsWithoutCost > 0), es mas alta de lo real.
 */
public record ProductRankingDto(
        ReservationItemType type,
        Long id,
        String name,
        long units,
        BigDecimal revenue,
        BigDecimal cost,
        BigDecimal profit,
        long unitsWithoutCost
) {
}
