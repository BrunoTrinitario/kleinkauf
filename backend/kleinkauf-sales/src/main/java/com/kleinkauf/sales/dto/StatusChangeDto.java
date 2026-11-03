package com.kleinkauf.sales.dto;

import com.kleinkauf.sales.entities.OrderStatus;

import java.time.LocalDateTime;

// userId solo en la vista del administrador (null para el comprador)
public record StatusChangeDto(
        OrderStatus status,
        LocalDateTime createdAt,
        String reason,
        Long userId
) {
}
