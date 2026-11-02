package com.decor.sales.dto.reports;

import java.math.BigDecimal;

/*
Totales de un conjunto de ventas.
- revenue: lo cobrado (totales de los pedidos, con el descuento ya restado).
- discounts: descuentos de las ventas del local.
- cost: costo de las unidades vendidas, con el costo guardado al vender.
- profit: revenue - cost. Si hay unidades sin costo cargado (unitsWithoutCost > 0), la ganancia es mas alta
  de lo real: el panel deberia avisarlo.
 */
public record ReportTotalsDto(
        long orders,
        long units,
        BigDecimal revenue,
        BigDecimal discounts,
        BigDecimal cost,
        BigDecimal profit,
        long unitsWithoutCost
) {
}
