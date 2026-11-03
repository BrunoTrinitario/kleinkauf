package com.kleinkauf.product.dto;

/*
El stock no se modifica aca: se usan los movimientos de stock (/api/v1/products/{id}/stock/movements).
 */
public record BasicUpdateDto(
        String name,
        String category,
        String description,
        int minStock
) {
}
