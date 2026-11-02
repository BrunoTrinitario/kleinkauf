package com.decor.common.dto.stock;

import java.util.List;

/*
Respuesta de la reserva: un item por cada linea pedida, en el mismo orden.
shortages: productos a los que les falto stock en una venta del local (se ajustaron); vacio si no falto nada.
 */
public record ReservationDto(
        String reference,
        List<ReservedItemDto> items,
        List<String> shortages
) {
}
