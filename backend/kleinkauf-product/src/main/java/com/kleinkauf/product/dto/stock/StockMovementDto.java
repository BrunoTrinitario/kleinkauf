package com.kleinkauf.product.dto.stock;

import com.kleinkauf.product.entities.StockMovementType;

import java.time.LocalDateTime;

public record StockMovementDto(
        Long id,
        Long productId,
        StockMovementType type,
        int quantity,
        int stockAfter,
        String reason,
        Long userId,
        LocalDateTime createdAt
) {
}
