package com.decor.common.dto.stock;

import java.util.List;

// Respuesta de la reserva: un item por cada linea pedida, en el mismo orden
public record ReservationDto(
        String reference,
        List<ReservedItemDto> items
) {
}
