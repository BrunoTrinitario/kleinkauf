package com.kleinkauf.sales.dto;

import com.kleinkauf.sales.entities.OrderStatus;

// Status change by the administrator. reason is required for RECHAZADO and CANCELADO
public record StatusUpdateDto(
        OrderStatus status,
        String reason
) {
}
