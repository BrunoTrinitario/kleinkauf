package com.decor.common.dto.stock;

import java.util.List;

/*
Pedido de reserva de stock de sales a products. reference es el numero del pedido:
products la usa para no descontar dos veces si sales reintenta.
 */
public record ReservationRequestDto(
        String reference,
        List<ReservationItemDto> items
) {
}
