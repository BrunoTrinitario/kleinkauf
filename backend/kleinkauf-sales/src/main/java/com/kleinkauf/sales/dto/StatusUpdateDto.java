package com.kleinkauf.sales.dto;

import com.kleinkauf.sales.entities.OrderStatus;

// Cambio de estado del administrador. reason es obligatorio para RECHAZADO y CANCELADO
public record StatusUpdateDto(
        OrderStatus status,
        String reason
) {
}
