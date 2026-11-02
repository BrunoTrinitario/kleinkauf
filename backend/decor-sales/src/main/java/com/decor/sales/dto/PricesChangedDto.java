package com.decor.sales.dto;

import java.util.List;

// Respuesta 409 al crear un pedido con precios viejos
public record PricesChangedDto(
        String message,
        List<PriceChangeDto> items
) {
}
