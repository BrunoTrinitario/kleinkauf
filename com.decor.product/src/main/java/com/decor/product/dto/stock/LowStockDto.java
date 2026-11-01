package com.decor.product.dto.stock;

public record LowStockDto(
        Long id,
        String name,
        int stock,
        int minStock
) {
}
