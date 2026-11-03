package com.kleinkauf.common.dto.stock;

import java.util.List;

/*
Pedido de reserva de stock de sales a products. reference es el numero del pedido:
products la usa para no descontar dos veces si sales reintenta.
local: venta del local. Acepta items sin precio ('a consultar') y, si falta stock, products registra
un AJUSTE por lo que falta (el stock queda en 0) y lo informa en ReservationDto.shortages.
 */
public record ReservationRequestDto(
        String reference,
        List<ReservationItemDto> items,
        boolean local
) {
}
