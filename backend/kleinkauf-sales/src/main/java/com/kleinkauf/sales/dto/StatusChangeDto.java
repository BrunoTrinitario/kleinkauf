package com.kleinkauf.sales.dto;

import com.kleinkauf.sales.entities.OrderStatus;

import java.time.LocalDateTime;

// userId only in the administrator view (null for the buyer)
public record StatusChangeDto(
        OrderStatus status,
        LocalDateTime createdAt,
        String reason,
        Long userId
) {
}
